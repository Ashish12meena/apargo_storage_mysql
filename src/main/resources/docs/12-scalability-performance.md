# 12 — Scalability and Performance

## 1. Current bottlenecks

| Bottleneck | Cause | Fix | Phase |
|---|---|---|---|
| Bytes traverse the service | Every upload spools to a temp file, then copies to the backend — a full extra disk write and read, and on the local provider a second full copy | Direct-to-S3 presigned upload | 3 |
| Per-JVM rate limiter | In-memory buckets; N replicas means N× the limit and non-deterministic 429s | Redis-backed | 1 |
| Local disk | No multi-instance story, no replication, no streaming | S3 only in prod | 3 |
| Presigner per call | Constructed and torn down inside try-with-resources on the hottest small operation | Singleton bean | 3 |
| Offset pagination | Degrades linearly with depth | Keyset | 2 |
| Schedulers on every replica | No coordination | Advisory locks | 1 |
| MySQL in every upload path | Quota reservation is synchronous | Accepted — see §4 | — |
| Executor rejection hangs callers | Handler logs and drops without completing the future | Complete exceptionally → 429 | 2 |

## 2. The primary lever

**Removing this service from the byte path** is worth more than every other
optimisation combined. It converts request duration from a function of file size to
a constant, and moves the throughput ceiling from one pod's disk to S3.

| | Today | Target |
|---|---|---|
| 100 MB upload | Full transfer through the JVM, temp file, request thread held for minutes | ~100 ms presign; bytes go client → S3 |
| Memory per upload | Proportional to concurrency × buffer | Constant |
| Throughput ceiling | Pod disk and network | S3 |

## 3. Scaling by dimension

| Dimension | Approach |
|---|---|
| Request rate | Horizontal. Stateless once local disk, in-JVM rate limiting, and ThreadLocal-dependent async work are gone. |
| Upload bandwidth | Direct-to-S3. Does not consume service capacity at all. |
| Download bandwidth | CDN. Reads never touch the service. |
| Media rows | Indexes now; partition by `organisation_id` at 50 M. |
| Tenants | No per-tenant state in memory. |
| Quota contention | Atomic conditional `UPDATE`. Contention is per project row, and per-project write rates are low. |

## 4. Quota is deliberately synchronous

MySQL is in the path of every upload. An asynchronous, eventually-consistent quota
model was considered and **rejected**: the entire value of a quota is the bound, and
a bound that lags lets a tenant overshoot by an unbounded amount during lag.

The cost is bounded — a single indexed `UPDATE` on a primary key, p95 under 20 ms,
and contention is per project row rather than global.

**If it ever does become the ceiling**, in order of preference: shard hot project
rows into N sub-rows summed on read; batch reservations for batch uploads (already
the design); only then consider Redis counters with periodic flush, accepting the
consistency cost explicitly.

Not doing this now. It is a solution to a problem that does not exist yet, and it
would trade a correct system for a faster approximate one.

## 5. Sizing

| Setting | Value | Reasoning |
|---|---|---|
| Tomcat threads | 200 | |
| DB pool | 30 | Threads deliberately exceed the pool: queue on the pool, not on MySQL |
| Container memory | 2 Gi | Heap 1 Gi via `MaxRAMPercentage=50`, headroom for direct buffers and metaspace |
| Container CPU | 1–2 vCPU | |
| Upload executor | core 10, max 30, queue 100 | Isolates upload backpressure from the rest of the app — a good decision in the current service, kept |
| HPA | 2–20 @ 70% CPU | Minimum 2 |

A 200-thread Tomcat pool against a 30-connection DB pool on an undersized container
is a classic self-inflicted OOM. These numbers are documented together for that
reason.

## 6. Caching

| What | Where | TTL | Why |
|---|---|---|---|
| Presigned URLs | Redis | Expiry window | Deterministic within a window; removes repeat presigner cost |
| API keys | In-memory | process lifetime | Loaded once at startup; constant-time compare per request, no I/O |
| CDN objects | CloudFront | 1 y (immutable keys) | Keys are never reused, so objects are safely immutable |
| Metadata | **None** | — | Not justified yet. Revisit if listing p99 exceeds SLO. |

`Cache-Control` must reflect sensitivity. The current local-serve path sets
`public, max-age=86400` **uniformly**, so a tenant's private document is cacheable
by any intermediate proxy for 24 hours with no invalidation path. Target: `private,
max-age=300` for authorized reads.

## 7. Load testing

Before Phase 3 exit:

| Scenario | Target |
|---|---|
| 500 uploads/s, 1 MB | p95 < 2 s, error rate < 0.1% |
| 100 concurrent uploads of 100 MB | No memory growth; direct-to-S3 |
| 5,000 metadata reads/s | p95 < 100 ms |
| Deep listing (page 500 equivalent) | p95 < 200 ms — validates keyset |
| Quota contention: 200 threads, one project | Exact accounting, no deadlock |
| Sustained 1 h at 70% capacity | No leak, no pool exhaustion, no drift |

## 8. Deferred, with triggers

| Work | Trigger |
|---|---|
| Kafka | Second independent consumer, or > 1,000 events/s |
| `media` partitioning | > 50 M rows |
| Read replica | Read load degrades writes |
| Metadata cache | Listing p99 exceeds SLO |
| Sharded quota rows | Reservation p95 > 50 ms |
| Multi-region | Compliance or latency requirement |

Each is a real option, and none is worth its operational cost today. Recording the
trigger is what stops the conversation being reopened every sprint.
