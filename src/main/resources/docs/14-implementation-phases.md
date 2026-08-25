# 14 — Implementation Phases

## Working agreement

Every unit of work, without exception:

```
Requirement → Documentation → Design/Contract → Implementation → Doc Update → Status Update
```

**No implementation begins until the documentation and contracts for that item are
written and internally consistent.** Skipping the first two steps is how this
service acquired two upload orchestrators with divergent semantics, a delete path
nothing calls, and a security model asserted in comments.

Each phase ends in a **deployable, revertible** state. No phase leaves the service
in a worse condition than it started.

---

## Phase 0 — Emergency (days 1–3)

**Nothing else starts until this is done.** These are live exposures, not
improvements.

| # | Task | Requirement |
|---|---|---|
| 0.1 | Deactivate the leaked IAM key; audit CloudTrail for its use | NFR-32 |
| 0.2 | Rotate the DB password | NFR-32 |
| 0.3 | Purge both from git history; force-push; re-clone all working copies | NFR-32 |
| 0.4 | Add `gitleaks` pre-commit hook and blocking CI gate | NFR-32 |
| 0.5 | **Authenticate `/internal/**`** — currently wide open; one call zeroes any tenant's quota | NFR-30 |
| 0.6 | Narrow actuator exposure; drop `env`, `beans`, `loggers`, `show-details: always` | NFR-32 |
| 0.7 | Remove default values from every secret | NFR-32 |
| 0.8 | Delete `application-storage.yml` | NFR-32 |

**Exit:** no live credential in the repository, no unauthenticated quota mutation.

---

## Phase 1 — Foundation (weeks 1–2)

Makes the service buildable, deployable, testable, and safe to run on more than one
replica. **No business logic.**

| # | Task | Requirement |
|---|---|---|
| 1.1 | `pom.xml`, Dockerfile, docker-compose (MySQL, Redis, MinIO), CI image build | NFR-40 |
| 1.2 | Package skeleton, ports, DTOs, ArchUnit boundary rules | — |
| 1.3 | Flyway `V1` baseline; seeds off the migration path; delete `db/storage.sql` | — |
| 1.4 | JWT verification, `TenantPrincipal`, `TenantContextFilter`; dual-accept window opens | NFR-30 |
| 1.5 | `MediaAccessGuard`; tenant-scoped repository signatures; delete tenant-blind methods | NFR-31 |
| 1.6 | Redis-backed rate limiting, layered keys, fail-open | NFR-14 |
| 1.7 | Advisory locks for scheduled jobs | NFR-14 |
| 1.8 | `ErrorCode`, unified envelope, `traceId`, filter-safe error writer | — |
| 1.9 | Correlation id + MDC + JSON logging | NFR-40 |
| 1.10 | Health indicators: storage, redis, migrations; liveness/readiness split | NFR-41 |

**Exit:** containerised, authenticated, multi-replica-safe, observable. Contracts
frozen for Phase 2.

---

## Phase 2 — Core correctness (weeks 3–5)

Closes every blocking defect. **This is the first genuinely production-ready state.**

| # | Task | Requirement |
|---|---|---|
| 2.1 | Migrations `V2`–`V7` | — |
| 2.2 | Domain aggregates with enforced state machines | — |
| 2.3 | `upload_session` two-phase reservation | FR-32, FR-33 |
| 2.4 | Consolidate quota to the single atomic implementation; **delete the other two** | FR-32 |
| 2.5 | Proxied upload, streaming, **no temp-file spool** | FR-01 |
| 2.6 | Tika magic-byte inspection; per-tenant limit profiles | FR-07, FR-44, NFR-34 |
| 2.6a | Quota hierarchy invariant: project limit ≤ org total | FR-30a, FR-30b, FR-30c |
| 2.7 | Idempotency keys on all mutating endpoints | FR-08 |
| 2.8 | **`DELETE /api/v1/media/{id}`** with real storage deletion, quota release, grace period | FR-20, FR-21, FR-22 |
| 2.9 | **`GET /api/v1/media/{id}`** | FR-10 |
| 2.10 | Keyset listing; type routes become aliases | FR-11 |
| 2.11 | `GET /api/v1/media/{id}/download-url`; retire `?storageKey=` | FR-12 |
| 2.12 | Outbox table, dispatcher, delete reaper | FR-21 |
| 2.13 | Session sweeper | FR-41 |
| 2.14 | Reconciliation: advisory-locked, drift metric, alert | FR-34 |
| 2.15 | Audit trail | FR-42 |
| 2.16 | Tenant quota self-service endpoint | FR-31 |
| 2.17 | Batch upload on the new pipeline; **fix the executor rejection handler** | FR-05 |
| 2.18 | Delete `MediaUploadOrchestrator.uploadMedia`, commented-out code, duplicate config — **migrate its read methods to `MediaQueryService` first** | — |

**Exit:** all blocking findings closed. Deployable to production.

---

## Phase 3 — Scale (weeks 6–8)

| # | Task | Requirement |
|---|---|---|
| 3.1 | Presigned direct upload, single + multipart | FR-02, FR-03, FR-04 |
| 3.2 | S3 adapter fixes: singleton presigner, CloudFront restored, SSE asserted, no silent fallback, conditional write | — |
| 3.3 | Local storage fenced out of prod (startup assertion) | — |
| 3.4 | Serve path: ownership check, `Range`, `ETag`, `private` caching, `attachment` | FR-13, FR-14, NFR-31 |
| 3.5 | WhatsApp push moved onto the outbox, out of the upload path | — |
| 3.5a | Decide whether to keep or delete the presigned-upload path (unused at 16 MB) | — |
| 3.6 | Full metrics, OpenTelemetry tracing | NFR-40 |
| 3.7 | Orphan detection | FR-40 |
| 3.8 | Permanent delete, batch delete, internal teardown | FR-23, FR-24, FR-25 |
| 3.9 | Quota threshold events | FR-35 |
| 3.10 | Terraform: bucket policy, lifecycle, IAM, KMS | NFR-35 |
| 3.11 | Load testing; HPA tuning | NFR-11 |
| 3.12 | Legacy header auth disabled in production | NFR-30 |

**Exit:** bytes no longer traverse the service; scales horizontally; verified under
load.

---

## Phase 4 — Hardening (weeks 9–12)

| # | Task | Requirement |
|---|---|---|
| 4.1 | Real scanner adapter (ClamAV/GuardDuty) behind the existing flag | FR-43 |
| 4.3 | Audit query API | FR-42 |
| 4.4 | Deprecation/sunset tooling; per-consumer usage metrics | — |
| 4.5 | Chaos testing for DB/storage consistency | NFR-25 |
| 4.6 | Drop deprecated columns (`media_url`, `media_id`, `waba_id`) | — |
| 4.7 | Runbooks, dashboards, alert thresholds | NFR-43 |
| 4.8 | Cross-region replication | NFR-22 |

**Exit:** operable by someone who did not build it.

---

## Testing

**No tests at this stage** — the current phase establishes structure and contracts
only. From Phase 1 onward, tests ship **with** the code they cover, never as a
follow-up ticket. Strategy is written before Phase 1 implementation begins.

Phase 1 minimum: ArchUnit boundary rules; security matrix (no token → 401,
cross-tenant → 404, header-spoof-with-token → token wins).

Phase 2 minimum: concurrency tests against real MySQL (100 threads × 1 MB against a
50 MB quota ⇒ exactly 50 succeed); idempotency under concurrency; path-traversal
matrix; LocalStack S3 tests — the `testcontainers:localstack` dependency is already
declared and entirely unused.

## Dependencies between phases

```
Phase 0 ──▶ Phase 1 ──▶ Phase 2 ──▶ Phase 3 ──▶ Phase 4
   │           │           │
   │           │           └─ 2.8 delete needs 2.12 outbox
   │           └─ 1.4 JWT blocks everything tenant-scoped
   └─ 0.5 internal auth is independent; ship immediately
```

**External dependency:** Phase 1.4 requires the gateway to issue JWTs. If that
slips, Phases 2 and 3 can proceed against a dev issuer, but the production cutover
in 3.12 cannot. This is the critical path item — see
[15-implementation-status.md](15-implementation-status.md), **BL-1**.
