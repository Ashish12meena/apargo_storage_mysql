# 15 — Implementation Status

> **LIVE DOCUMENT.** Updated at the end of every work item, before the next one
> starts. If this is stale, the process has already broken.

**Last updated:** 2026-08-23 (auth model changed)
**Current phase:** Phase 1 + Phase 2 implemented · parts of Phase 3 implemented
**Overall:** Full implementation of the core service. Not yet built or tested —
see **Verification status** below, which is the most important section on this page.

## ⚠️ Verification status

The build environment used to write this code had **no Maven and no access to
Maven Central**, so the project has never been compiled end to end.

| Layer | Status |
|---|---|
| `domain`, `common`, `application.port`, `application.command`, `application.query` | **Compiles clean** — 94 classes, `javac -Xlint:all`, zero warnings |
| `application.service`, `api`, `infrastructure`, `config` | **Not compiled.** Verified only by static analysis: import resolution, port-to-adapter method coverage, and manual review |

Before anything else: run `mvn -B clean package` and fix what falls out. Expect
signature drift against the AWS SDK, Tika, and springdoc versions pinned in
`pom.xml` — those APIs could not be checked against real jars. Treat the first
green build as the real start of Phase 1.

---

## ✅ Completed

### Documentation (this phase)

| Item | Document |
|---|---|
| Architecture and responsibilities | `01-architecture.md` |
| Package structure and boundaries | `02-package-structure.md` |
| Functional and non-functional requirements | `03-requirements.md` |
| API contracts and compatibility | `04-api-contracts.md` |
| Domain design and business rules | `05-domain-design.md` |
| Database design and scale plan | `06-database-design.md` |
| Messaging design | `07-messaging-design.md` |
| Integrations | `08-integrations.md` |
| Security requirements | `09-security.md` |
| Error-handling strategy | `10-error-handling.md` |
| Production readiness | `11-production-readiness.md` |
| Scalability and performance | `12-scalability-performance.md` |
| Configuration requirements | `13-configuration.md` |
| Implementation phases | `14-implementation-phases.md` |
| Architecture decisions | `adr/ADR-001` … `ADR-011` |
| Risks and assumptions | `17-risks-assumptions.md` |
| Definition of done | `18-definition-of-done.md` |

### Phase 1 — Foundation

| Item | Location |
|---|---|
| `pom.xml` — Spring Boot 3.5.6, Java 21, Maven | `pom.xml` |
| Dockerfile (multi-stage, non-root UID 10001, healthcheck) | `Dockerfile` |
| Compose stack: MySQL, Redis, MinIO | `docker-compose.yml` |
| Flyway `V1`–`V7`; seeds off the migration path | `src/main/resources/db/migration` |
| API key authentication, `TenantPrincipal`, `TenantContextFilter` | `api/security` |
| `InternalCallerFilter` — closes the unauthenticated `/internal` surface | `api/security` |
| `MediaAccessGuard`; tenant-scoped repository signatures | `api/security`, `application/port/out` |
| Redis token-bucket rate limiting (Lua), layered keys, fail-open | `infrastructure/ratelimit` |
| Database lease locks for schedulers | `infrastructure/persistence/repository` |
| `ErrorCode`, envelope with `traceId`, filter-safe error writer | `common/error`, `api/error` |
| Trace id + MDC + JSON logging; trusted-proxy IP resolution | `infrastructure/observability` |
| Health indicators: storage, outbox; liveness/readiness split | `infrastructure/observability` |
| Startup assertions (prod + local storage fails the boot) | `config/StartupAssertions.java` |
| `.gitleaks.toml` secret-scanning gate | `.gitleaks.toml` |

### Phase 2 — Core correctness

| Item | Location |
|---|---|
| Domain aggregates with enforced state machines | `domain/media`, `domain/upload` |
| Two-phase quota reservation via `upload_session` | `domain/upload`, `application/service` |
| Single atomic quota implementation (conditional UPDATE) | `infrastructure/.../QuotaJdbcAdapter.java` |
| Proxied upload, streaming, **no temp-file spool** | `application/service/MediaUploadService.java` |
| Tika magic-byte inspection; single allowlist | `infrastructure/inspection` |
| Idempotency keys wired into the upload path | `application/service/IdempotencyGuard.java` |
| **`DELETE /api/v1/media/{id}`** with quota release + grace period | `application/service/MediaDeletionService.java` |
| **`GET /api/v1/media/{id}`**, keyset listing, download-url | `api/rest/MediaController.java` |
| Outbox table, dispatcher, media reaper, purge scan | `infrastructure/outbox`, `infrastructure/scheduler` |
| Session sweeper, quota reconciliation, orphan scan | `application/service/StorageReconciliationService.java` |
| Append-only audit trail | `infrastructure/.../AuditAdapter.java` |
| Tenant quota self-service endpoint | `api/rest/QuotaController.java` |
| **Tenant teardown** — project and org, async, batched | `api/internal/InternalMediaController.java` |
| `purge_after` — makes `?permanent=true` actually immediate | `V8`, `MediaDeletionService` |

### Phase 3 — partially implemented

| Item | Status |
|---|---|
| Presigned direct upload (single + multipart) | Implemented |
| S3 adapter: singleton presigner, CloudFront, SSE, no silent fallback | Implemented |
| Local storage fenced out of prod | Implemented |
| Serve path: ownership check, Range, ETag, private caching, attachment | Implemented |
| Orphan detection | Implemented |
| Full metric set, OpenTelemetry tracing | Partial — core metrics only |
| Terraform for bucket policy, lifecycle, IAM, KMS | **Not started** |
| Load testing, HPA tuning | **Not started** |
| WhatsApp push moved onto the outbox | **Not started** (OD-1 unresolved) |

### Earlier: structure and contracts

| Item | Location |
|---|---|
| Package skeleton with boundary `package-info.java` | `com.aigreentick.services.storage.*` |
| Domain value objects and enums | `domain/media`, `domain/quota`, `domain/upload`, `domain/shared` |
| Domain aggregate signatures (no bodies) | `Media`, `Quota`, `UploadSession` |
| Domain events | `domain/event/DomainEvent` |
| Exception hierarchy with error codes | `domain/exception/*` |
| Inbound ports (5) | `application/port/in/*` |
| Outbound ports (11) | `application/port/out/*` |
| Commands and query/view types | `application/command`, `application/query` |
| API DTOs | `api/dto/request`, `api/dto/response` |
| Security contracts | `api/security/*` |
| Controller placeholders | `api/rest`, `api/internal` |
| Infrastructure adapter placeholders | `infrastructure/**` |
| Typed configuration records | `config/properties/*` |
| `ErrorCode`, `ApiPaths`, `HeaderNames` | `common/*` |

**Explicitly NOT done:** no tests of any kind (see Next), no Terraform, no
Kubernetes manifests, no structural validation limits, no malware scanning.

---

## 🔄 Currently working

*Nothing.* Awaiting the first green build.

---

## ⏭️ Next

In order. Items 1–5 are Phase 0 and remain the highest priority — they are live
exposures in the **predecessor** service, which is still deployed.

| # | Item | Phase | Blocked by |
|---|---|---|---|
| 1 | Deactivate the leaked IAM key, audit CloudTrail | 0 | **BL-2** |
| 2 | Rotate DB password, purge git history | 0 | BL-2 |
| 3 | Wire `gitleaks` into CI as a blocking gate | 0 | — |
| 4 | Deploy internal-API authentication to the predecessor | 0 | BL-3 |
| 5 | Narrow the predecessor's actuator exposure | 0 | — |
| 6 | **`mvn clean package` — first green build** | 1 | — |
| 7 | ArchUnit boundary rules | 1 | 6 |
| 8 | Security test matrix: no token → 401, cross-tenant → 404, header-spoof → token wins | 1 | 6 |
| 9 | Concurrency suite against real MySQL (100 threads × 1 MB vs a 50 MB quota) | 2 | 6 |
| 10 | LocalStack/MinIO integration tests for `S3StorageAdapter` | 2 | 6 |
| 11 | Idempotency-under-concurrency and path-traversal tests | 2 | 6 |

## 🚧 Blocked

| ID | Item | Blocked by | Impact | Owner |
|---|---|---|---|---|
| ~~**BL-1**~~ | ~~Production cutover to JWT-only auth~~ | **RESOLVED 2026-08-23 — no longer blocked.** JWT withdrawn in favour of per-service API keys (ADR-010). No gateway dependency remains. | — | — |
| **BL-2** | Credential rotation (0.1–0.3) | Needs AWS console access and a maintenance window for the force-push | **Highest severity.** A live-format key is in the repository right now. | Platform / Security |
| **BL-3** | Internal API auth | Organisation-service team must send `X-Api-Key` with a `quota:admin` key | Coordinated deploy; breaking otherwise. Simpler than the withdrawn JWT plan — one header. | Org service team |
| **BL-4** | Removing WhatsApp push (ADR-009) | WABA team must agree to consume `media.created` | Falls back to the transitional shim if declined. | WABA team |

---

## ❓ Open decisions

| ID | Decision | Options | Needed by | Default if undecided |
|---|---|---|---|---|
| **OD-9** | Accept the confused-deputy risk, or pin callers with `fixed-org-id`? | An authenticated caller may assert any tenant. Pinning closes it per-caller but only suits single-tenant integrations. | Before production | Accept, with startup warnings |
| **OD-8** | Delete the presigned-upload path, or keep it disabled? | It is unused at 16 MB. Keeping unused code has a cost; deleting it makes a future large-file requirement a re-architecture. | End of Phase 3 | Keep, disabled |
| **OD-1** | Does WhatsApp publishing stay in this service? | (a) Remove — `waba-service` consumes `media.created` (**recommended**, ADR-009) · (b) Keep behind the outbox | Phase 3 | (b) — transitional shim |
| **OD-2** | Public media id format | (a) Keep auto-increment `BIGINT` · (b) ULID/UUID public id with the surrogate key internal (**recommended** — current ids are enumerable and leak volume) | **Phase 2 start** — schema depends on it | (a), and accept the enumeration risk |
| **OD-3** | Who owns quota *limits*? | (a) Storage service (today) · (b) Organisation service, which knows the billing plan · (c) Split — org service owns limits, we own consumption (**recommended**) | Phase 2 | (a) |
| ~~**OD-4**~~ | ~~Is project-level quota actually used?~~ | **RESOLVED 2026-08-22 — keep both levels.** Projects genuinely have different limits, set at creation or later. New invariant added: a project limit may never exceed its org total, enforced at provisioning. | — | — |
| **OD-5** | Delete grace period | 7 days assumed. Compliance may require shorter or longer. | Phase 2 | 7 days |
| **OD-6** | Does `template-service` store URLs, ids, or both? | Determines whether the v2 response can drop `storedFilename` | Phase 3 | Assume both; keep the field deprecated |
| ~~**OD-7**~~ | ~~`MediaType.PRODUCT`~~ | **RESOLVED — removed.** No upload path, no validation rule, no consumer. | — | — |

---

## 📦 Deferred

Recorded with a trigger so the conversation is not reopened every sprint.

| Item | Trigger to revisit |
|---|---|
| Structural validation (pixel/page counts) | **Never here.** Belongs in whatever component decodes files (ADR-013) |
| Real malware scanner adapter | Flip `media.scanning.enabled` — capability already wired (ADR-014) |
| Per-tenant file-size limits (`limit_profile` column) | A tenant genuinely needs a different per-file ceiling (ADR-012) |
| Kafka | Second independent consumer, or > 1,000 events/s |
| `media` table partitioning | > 50 M rows |
| Redis metadata cache | Listing p99 exceeds SLO |
| Sharded quota rows | Reservation p95 > 50 ms |
| Read replica | Read load degrades writes |
| Multi-region replication | Compliance or latency requirement |
| Thumbnail / transcode pipeline | Product requirement |
| Cross-tenant deduplication | Never — ADR-011 |
| Tenant-visible file versioning | Product requirement |
| GraphQL / gRPC surface | Consumer demand |

---

## Update protocol

At the end of **every** work item:

1. Move the item from **Next** → **Completed**, with its date.
2. Update the affected document(s) if the design changed during implementation.
3. Record any new blocker or open decision, with an owner.
4. Pull the next item into **Currently working**.

A completed item whose documentation was not updated is **not complete** — see
[18-definition-of-done.md](18-definition-of-done.md).
