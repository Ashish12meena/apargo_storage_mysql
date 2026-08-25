# 11 — Production Readiness

## 1. Observability

### 1.1 Metrics

The current service has exactly two custom metrics (`file.cleanup.success/failure`).
None of the numbers you would put on a media-service dashboard exist.

| Metric | Type | Tags |
|---|---|---|
| `storage.upload.duration` | Timer | `provider`, `media_type`, `path`, `outcome` |
| `storage.upload.bytes` | Counter | `org_id`, `media_type` |
| `storage.download.duration` | Timer | `provider`, `outcome` |
| `storage.quota.rejected` | Counter | `scope`, `org_id` |
| `storage.quota.utilisation` | Gauge | `org_id` |
| `storage.quota.drift.bytes` | Gauge | `org_id` — **non-zero means a bug** |
| `storage.provider.errors` | Counter | `provider`, `operation`, `error_type` |
| `storage.ratelimit.rejected` | Counter | `endpoint`, `layer` |
| `storage.ratelimit.degraded` | Counter | — (fail-open events) |
| `storage.session.expired` | Counter | — |
| `storage.orphans.reclaimed` | Counter | `provider` |
| `storage.outbox.lag.seconds` | Gauge | `event_type` — oldest PENDING age |
| `storage.outbox.failed` | Counter | `event_type` |
| `storage.validation.rejected` | Counter | `reason` |
| `storage.auth.rejected` | Counter | `reason` |

**Cardinality:** `org_id` is bounded and business-critical, so it is an acceptable
tag. `user_id`, `media_id`, and `storage_key` are unbounded and go to logs, never
to labels.

### 1.2 Tracing

No distributed tracing exists. `traceId` is locally generated and never propagated,
so a request cannot be followed across the Eureka mesh.

`micrometer-tracing-bridge-otel` with an OTLP exporter. W3C `traceparent`
propagated to every downstream. Spans around: quota reservation, storage write,
content inspection, outbox dispatch — the four places latency actually goes.
Sampling: 100% of errors and slow requests, 1–10% baseline.

### 1.3 Logging

JSON in production. MDC on every request: `traceId`, `requestId`, `orgId`,
`projectId`, `userId`, `mediaId`. None of this exists today.

| Level | Use |
|---|---|
| ERROR | Requires human attention. Alertable. |
| WARN | Suspicious or degraded — 403s, content mismatches, fail-open events |
| INFO | State changes: upload committed, media deleted, quota provisioned |
| DEBUG | Diagnostics. Storage keys and filenames appear only here. |

**One log line per state change, at the boundary.** The current service logs the
tenant context at INFO on every request, which is per-request noise that also
correlates tenants to traffic patterns.

## 2. Health checks

Actuator's default `UP` says nothing about whether this pod can write to S3 or
whether the disk is full — the two failure modes this service is most exposed to.

| Indicator | Checks | Cache |
|---|---|---|
| `db` | Pool and connectivity | — |
| `storage` | `HeadBucket` (S3) or writability + free space (local) | 30 s |
| `redis` | Ping — reports **DEGRADED**, never DOWN | 30 s |
| `outbox` | Dispatch lag under threshold | 60 s |
| `quotaConsistency` | Drift under threshold | 5 min |
| `migrations` | Flyway is at the expected version | startup |

| Probe | Composition | Failure means |
|---|---|---|
| **Liveness** | JVM responsive only | Restart the pod |
| **Readiness** | db + storage + migrations | Remove from the load balancer, **do not restart** |
| **Startup** | migrations complete | Still booting |

Redis is deliberately excluded from readiness: rate limiting fails open, so a Redis
outage must not take the fleet out of rotation.

## 3. Alerts

| Alert | Condition | Severity |
|---|---|---|
| Upload error rate | > 5% over 5 min | P1 |
| Storage provider errors | > 10/min | P1 |
| Quota drift | any org non-zero after reconciliation | **P1 — indicates a bug** |
| Outbox lag | > 5 min | P2 |
| Outbox failures | any row reaches FAILED | P2 |
| Rate limiter degraded | any fail-open event | P2 |
| Auth rejections | > 100/min from one source | P2 — possible attack |
| Content mismatch | > 10/min | P2 — possible attack |
| Orphans reclaimed | > 0 | P3 — investigate the cause, do not just accept the cleanup |
| Quota utilisation | org > 95% | P3 |

Every alert has a runbook. An alert without one is noise that gets muted.

## 4. Deployment

### Container

No Dockerfile, compose file, or container build configuration exists anywhere in
the repository today, so there is currently no reproducible way to build a
deployable artifact from this repo alone.

- Multi-stage: dependency layer cached separately from application classes
- JRE-only runtime on distroless or Alpine
- **Non-root UID 10001**
- Read-only root filesystem, `tmpfs` for `/tmp`
- `HEALTHCHECK` against `/actuator/health/readiness`
- `-XX:MaxRAMPercentage=75` so the JVM respects container limits, not host memory

Built **once** in CI, scanned with Trivy, signed, and the **same digest** promoted
through staging to production. Never rebuilt per environment.

### Kubernetes

- Probes wired to the three distinct actuator endpoints
- Requests and limits matched to [12 §5](12-scalability-performance.md)
- HPA 2–20 replicas at 70% CPU. **Minimum 2**, so single-instance assumptions
  cannot silently return
- `PodDisruptionBudget: minAvailable: 1`
- `terminationGracePeriodSeconds: 60` with graceful shutdown, so in-flight uploads
  drain
- NetworkPolicy restricting `/internal` to the organisation-service selector
- IRSA for S3

### Scheduling

Spring's default `TaskScheduler` is **single-threaded**. This service runs a
1-second outbox poll alongside jobs that take minutes, so on one thread a slow
nightly reconciliation pass blocks outbox dispatch entirely — deletes stop being
reaped for as long as it runs.

`SchedulingConfig` therefore declares a pool (`scheduling.pool-size`, default 4)
sized above the number of long-running jobs. It also installs an error handler:
an uncaught exception in a scheduled task otherwise cancels its future silently,
and that job never runs again for the life of the process.

`scheduling.enabled=false` disables all of it, for tests or for running a web-only
replica set with maintenance on a dedicated deployment.

### Rollout

Rolling update, `maxSurge: 1`, `maxUnavailable: 0`. Migrations run as an init
container or a separate job, never concurrently across replicas. Because every
migration is backward-compatible with the previous release, old and new pods
coexist safely during the rollout.

## 5. Failure modes and recovery

| Failure | Detection | Recovery |
|---|---|---|
| Crash after quota reserve, before object write | Session TTL | Sweeper releases quota |
| Crash after object write, before commit | Session TTL | Sweeper deletes the object and releases quota |
| S3 write fails | Synchronous error | Quota released, 502; no row created |
| DB commit fails after object written | Session stays RESERVED | Sweeper deletes the object |
| Storage delete fails during reap | Outbox `attempts` | Backoff, then DLQ + alert |
| Quota drift | Nightly reconciliation | Recompute and **alert** — do not silently heal |
| Orphaned object | Nightly orphan scan | Delete and emit a metric |
| Redis down | Health check | Fail open, alert |
| S3 degraded | Circuit breaker | Uploads 503; CDN reads survive |
| MySQL down | Readiness | Pod leaves the LB, is not restarted |

**Every one of these resolves to a sweeper or a retry, not to compensating code in
a `catch` block.** Compensation in a catch block does not run when the process dies,
which is precisely when it is needed — the current service's quota rollback has
exactly this shape.

## 6. Backup and recovery

| Asset | Mechanism | RPO | RTO |
|---|---|---|---|
| MySQL | Automated snapshots + binlog PITR | 5 min | 1 h |
| S3 objects | Versioning + cross-region replication (P4) | ~0 | minutes |
| Config | Git + Secrets Manager versioning | 0 | minutes |

Restore is **tested quarterly**. A backup that has never been restored is a
hypothesis.
