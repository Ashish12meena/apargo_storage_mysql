# 01 — Architecture and Service Responsibilities

## 1. Purpose

The Storage Service is the **system of record for uploaded files and the storage
quota that governs them**. It answers three questions: what files exist, who may
touch them, and how much space a tenant has consumed.

## 2. Responsibilities

**Owns:**

| Responsibility | Notes |
|---|---|
| File metadata | Filename, size, type, checksum, lifecycle state, ownership |
| Storage placement | Key generation, backend selection, object lifecycle |
| Quota accounting | Reservation, release, reconciliation at org and project scope |
| Resource authorization | Whether a verified principal may act on a specific object |
| Content validation | Type allowlisting, magic-byte verification, size limits |
| Lifecycle | Upload → active → deleted → purged, and the jobs that drive it |

**Explicitly does NOT own:**

| Not ours | Owner | Why it matters |
|---|---|---|
| User authentication | API Gateway | We verify assertions; we never authenticate |
| Org/project registry | Organisation Service | We store tenant ids, not tenant records |
| WhatsApp media publishing | `waba-service` | See §5 and ADR-009 — currently violated |
| Image/video transcoding | Not built | If added, a separate service consuming our events |
| Billing | Billing service | We report consumption; we do not price it |

## 3. Architectural style

**Hexagonal (ports and adapters)**, retained from the current service because the
existing boundary is real rather than decorative: `domain` depends on nothing,
`StoragePort` genuinely isolates the backend, and the documented layering matches
the code. That is rarer than it should be and is worth keeping.

```
        ┌──────────────────────────────────────────────┐
        │                    api                       │  inbound adapters
        │  controllers · filters · DTOs · error map    │
        └───────────────────────┬──────────────────────┘
                                │ depends on
        ┌───────────────────────▼──────────────────────┐
        │                application                   │  use cases + ports
        │   port.in (what we offer)                    │
        │   port.out (what we need)                    │
        └───────────────────────┬──────────────────────┘
                                │ depends on
        ┌───────────────────────▼──────────────────────┐
        │             domain  ·  common                │  no dependencies
        └───────────────────────▲──────────────────────┘
                                │ implements port.out
        ┌───────────────────────┴──────────────────────┐
        │              infrastructure                  │  outbound adapters
        │  JPA · S3 · Redis · Tika · outbox · clients  │
        └──────────────────────────────────────────────┘
```

Dependencies point inward. Infrastructure implements interfaces the application
owns, so a backend swap does not touch business logic.

## 4. Control plane, not data plane

The defining decision (ADR-004): **this service manages files; it does not carry
their bytes.**

Today every uploaded byte passes through the JVM heap, a temp file on local disk,
and the servlet container, and in local mode every downloaded byte does too. That
makes throughput a function of one pod's disk rather than of S3, and makes every
file size a latency and memory question.

Target: clients exchange bytes with S3 directly via short-lived presigned URLs.
This service reserves quota, records metadata, authorizes, and confirms.

```
  client ──▶ gateway ──▶ storage-service        (metadata, quota, authz)
    │                          │
    │                          ├──▶ MySQL       (source of truth)
    │                          ├──▶ Redis       (rate limiting only)
    │                          └──▶ outbox      (async effects)
    │
    └──────── bytes ──────────▶ S3 / CloudFront (never through us)
```

Consequences: request duration decouples from file size; the service becomes
genuinely stateless; a 500 MB upload no longer occupies a request thread for
minutes.

## 5. Boundary violation to correct

The current service performs WhatsApp media publishing inside the upload path:
`MediaUploadOrchestrator` calls `waba-service` and then the Meta Graph API
synchronously, with a 60-second read timeout, before returning to the caller. This
drags a WhatsApp domain concern, a `X-Waba-Id` header, Meta credentials, and
Facebook-specific resilience configuration into a service that should only know
about files.

**Recommendation:** this service emits `media.created`; `waba-service` subscribes
and performs its own push, fetching bytes via presigned URL. That removes an entire
integration package and a credential dependency from this service and puts WhatsApp
logic where WhatsApp logic belongs. Tracked as **OD-1** — it requires agreement
from the WABA team.

## 6. Quality attributes, in priority order

1. **Tenant isolation** — a cross-tenant read is the worst outcome available. Every
   other property is negotiable against this one.
2. **Durability and consistency** — a file that is acknowledged must exist; quota
   must reflect reality.
3. **Availability** — degraded beats down. Rate limiting fails open; CDN reads
   survive a control-plane outage.
4. **Scalability** — horizontal, stateless, with S3 as the throughput ceiling.
5. **Latency** — important, and deliberately last: correctness first.

## 7. Baseline audit

The service being redesigned is Spring Boot 3.5 / Java 21, ~108 source files.

### Worth keeping

- **Hexagonal layering** — real and enforced.
- **`StoragePort`** — clean backend abstraction.
- **Quota algebra** — the atomic conditional `UPDATE` is correct and lock-free.
  Lock ordering (project, then org) is documented and consistently applied, which
  is what actually prevents deadlocks. Nightly reconciliation self-heals drift.
  This is better than most production quota implementations.
- **Exception-to-status mapping** — including the deliberate 507 for quota,
  preserved for downstream compatibility.
- **Storage key layout** — `org-{id}/proj-{id}/{type}/{uuid}` enables prefix-scoped
  IAM and lifecycle rules.
- **Response envelope** — consistently applied.
- **Existing unit tests** — validators, quota, batch upload. All retained.

### Must be replaced

| Area | Current state |
|---|---|
| Authorization | Tenant identity read verbatim from `X-Org-Id` / `X-Project-Id` with zero verification. Any caller reaching the port can act as any tenant. |
| Internal API | `/internal/quota/**` has **no authentication at all**. One unauthenticated `PUT` zeroes any tenant's quota. Also outside the rate limiter, which covers `/api/**` only. |
| Delete | Four delete methods, none reachable from a controller, none calling `storagePort.delete()`, two never releasing quota. Storage usage only ever grows. |
| Content validation | Only the client-declared MIME type is checked. No byte is ever read. |
| Rate limiting | In-JVM buckets. With N replicas the limit is N×, and 429s depend on load-balancer routing. |
| Secrets | A live-format AWS access key and secret, plus a DB password, committed in plaintext. |
| Serve path | Explicitly excluded from the context interceptor, so it has no tenant context and performs no ownership check. |

### Defects found in code, beyond the prior review

1. `softDeleteById(mediaId, deletedBy)` silently discards `deletedBy`; no such
   column exists.
2. `deleteById(Long)` takes no tenant scope — wiring it up is a direct IDOR.
3. `deleteByOrgAndProject` / `deleteByOrganisation` never release quota.
4. `generatePresignedUrl` falls back to an unsigned URL for a PRIVATE object on
   failure — guaranteed 403, surfacing as a broken file.
5. A DB failure after a successful S3 write releases quota but never deletes the
   object. The orphan is invisible to reconciliation, which sums DB rows.
6. `UserContextInterceptor` calls `Long.valueOf` unguarded — a non-numeric header
   yields a 500 rather than a 400.
7. `MediaController` uses `MediaUploadOrchestrator` for all read paths, so the
   "dead" orchestrator is half-live and cannot simply be deleted.
8. `spring.flyway.locations` points at `classpath:db/migration`, which does not
   exist, while `ddl-auto: validate` requires a schema only `db/storage.sql`
   provides — a script whose first statement is `DROP SCHEMA`.
9. `GlobalExceptionHandler` returns `ex.getMessage()` to clients for storage and
   not-found errors; those messages embed storage keys.
10. `ApiPaths` is an empty stub while real paths are literals in controllers.
11. `mediaUploadExecutor`'s rejection handler logs and drops without completing the
    future — a caller of `uploadBatch` blocks forever under queue exhaustion.

### Disposition

Roughly **35% kept, 40% modified, 20% replaced, 5% deleted**. Per-component detail
in [02-package-structure.md §5](02-package-structure.md).

## 8. Audit events

Every business change, and every business failure after a request was accepted,
is published as an audit event to Kafka (topic `apargo.audit.event`) following the
platform *Audit Events — Implementation Guide*. The platform contract lives in
`com.apargo.platform.contract` and is copied unchanged from `template-service`.

### 8.1 Guarantees

- **Auditing never affects business logic.** Building an event cannot throw into
  the caller (`StorageAuditEvents` catches and logs); publishing cannot throw
  into the caller (`AuditEventPublisher` catches and logs). A Kafka outage, a
  full audit queue or an invalid event is logged at ERROR with the full event
  JSON for replay, and the API still returns exactly what it would have.
- **Only committed changes.** Events are published `AFTER_COMMIT`; a rolled-back
  change is never audited. Events raised outside a transaction are published at
  once (each such write has already committed).
- **No request waits for Kafka.** The send runs on `auditPublisherExecutor`
  (bounded queue, no caller-runs: a full queue drops and logs).
- **Best effort, at-least-once from the producer's side.** `acks=all`,
  idempotent producer, retried until `delivery.timeout.ms`. The audit service
  de-duplicates on `eventId` (UUIDv7, the Kafka key).
- `AUDIT_ENABLED=false` turns publishing off; APIs behave identically.

### 8.2 One owner per concern

| Concern | Owner |
|---|---|
| Trace id for requests (new for public calls, continued from `traceparent` on `/internal/**` only, never returned as a header) | `TraceContextFilter` |
| `requestId`, `userId`, `internalCaller`, org / project / caller in the MDC | `RequestIdFilter` |
| Trace id and job name for scheduled runs | `ScheduledJobContext.run(ScheduledJobs.X, …)` |
| MDC on pool threads | `MdcTaskDecorator` (scheduler and audit pool) |
| Actor, channel, request / trace id of an event | `AuditContextProvider` |
| Building events | `StorageAuditEvents` (the only factory) |
| Event types / names, keys, codes, messages | `StorageAuditEventType`, `AuditConstants` |
| Error → audit category | `AuditErrorMapper` |
| Kafka | `AuditEventPublisher` (the only class using Kafka) |
| Tunables | `audit.*` → `AuditProperties` |

`meta.traceId` in every response is the key that finds a request's log lines
(`trace=` in the log pattern) and its audit events.

### 8.3 Actor and channel

| Situation | `actor` | `channel` |
|---|---|---|
| Request with `X-User-Id` | `USER` / user id | `WEB` |
| `/internal/**` call without a user | `SERVICE` / authenticated client id | `API` |
| Work handed to a pool thread by a request | the request's actor | `WORKER` |
| Scheduled job (including outbox handlers) | `SYSTEM` / job name | `WORKER` |
| Anything else (e.g. `/api/**` without `X-User-Id`) | `SERVICE` / `unknown` | `API` |

Callers of `/api/**` must forward `X-User-Id` for the acting user to appear.
`ip` and `userAgent` stay empty until the gateway supplies trusted values.

### 8.4 Event catalogue

Module `STORAGE`. `changes` is `[]` for creates and deletes. Metadata holds ids,
enum names, counts and flags only. Never in an event: original filenames, storage
keys, presigned URLs, request bodies, stack traces, credentials.

| Event type | Trigger | Actor / channel | Entity | Changes | Metadata |
|---|---|---|---|---|---|
| `MEDIA_UPLOADED` | `POST /media/upload`; each file of `POST /media/upload/batch`; `POST /media/uploads/{id}/complete` | request | `MEDIA` | `[]` | `mediaType`, `contentType`, `sizeBytes`, `uploadMode`, `uploadSessionId` |
| `MEDIA_UPLOADED` (FAILURE) | Completing an accepted presigned session failed after the bytes landed; session aborted | request | `MEDIA` | `[]` | `mediaType`, `declaredSizeBytes`, `uploadMode`, `uploadSessionId`, `quotaReleased`; `error` |
| `UPLOAD_SESSION_CREATED` | `POST /media/uploads` | request | `UPLOAD_SESSION` | `[]` | `mediaId`, `mediaType`, `uploadMode`, `declaredSizeBytes` |
| `UPLOAD_SESSION_ABORTED` | `DELETE /media/uploads/{id}` that actually aborted a session | request | `UPLOAD_SESSION` | `status` | `mediaId`, `uploadMode`, `reclaimedBytes` |
| `UPLOAD_SESSION_EXPIRED` | Session sweeper | `SYSTEM/sweep-sessions` | `UPLOAD_SESSION` | `status` | `mediaId`, `uploadMode`, `reclaimedBytes` |
| `MEDIA_DELETED` | `DELETE /media/{id}`; each id of `DELETE /media/batch` | request | `MEDIA` | `[]` | `mediaType`, `sizeBytes`, `permanent` |
| `MEDIA_RESTORED` | `POST /media/{id}/restore` | request | `MEDIA` | `status` | `sizeBytes` |
| `MEDIA_PURGED` | Purge scan; outbox reaper | `SYSTEM/purge-media`, `SYSTEM/outbox-dispatch` | `MEDIA` | `[]` | `previousStatus`, `sizeBytes` |
| `MEDIA_SCANNED` | Scan handler (only with scanning enabled) | `SYSTEM/outbox-dispatch` | `MEDIA` | `scanStatus`, `status` when quarantined | `sizeBytes` |
| `QUOTA_CREATED` | `PUT /internal/quota/org` or `/project`, no limit before | internal caller | `ORG_QUOTA` / `PROJECT_QUOTA` | `[]` | `scope`, `maxBytes` |
| `QUOTA_UPDATED` | Same, limit changed (unchanged → no event) | internal caller | `ORG_QUOTA` / `PROJECT_QUOTA` | `maxBytes` | `scope` |
| `QUOTA_RECONCILED` | Nightly reconciliation found drift | `SYSTEM/reconcile-quota` | `PROJECT_QUOTA` | `usedBytes` | `driftBytes` |
| `STORAGE_TEARDOWN_REQUESTED` | `DELETE /internal/media/org/{org}` or `/project/{org}/{project}` | internal caller | `STORAGE_TEARDOWN` (job id) | `[]` | `scope`, `permanent`, `estimatedFiles` |
| `STORAGE_TEARDOWN_COMPLETED` | Teardown handler: no live files remain | `SYSTEM/outbox-dispatch` | `STORAGE_TEARDOWN` | `[]` | `scope`, `filesRemoved`, `batches` |
| `STORAGE_TEARDOWN_COMPLETED` (FAILURE) | Teardown dead-lettered | `SYSTEM/outbox-dispatch` | `STORAGE_TEARDOWN` | `[]` | `scope`, `filesRemoved`, `batches`, `attempts`; `error` |
| `STORAGE_ORPHANS_RECLAIMED` | Orphan scan removed objects for a tenant (0 → no event) | `SYSTEM/reclaim-orphans` | — | `[]` | `reclaimedCount`, `storageProvider` |

**Not audited:** reads (list, get, download-url, `/serve` streaming), 400 / 422
validation errors, 404 / 409 / 507 pre-checks that changed nothing, idempotent
replays and repeat deletes / commits, technical failures of a request that left
nothing changed (logs, by `traceId`), quota threshold alerts, and cleanup of
dispatched outbox rows and expired idempotency records.

**Error categories** (`AuditErrorMapper`): validation codes → `VALIDATION`;
`UNAUTHENTICATED` → `AUTHENTICATION`; `FORBIDDEN` → `AUTHORIZATION`;
`STORAGE_UNAVAILABLE`, `DEPENDENCY_FAILURE` → `EXTERNAL_SERVICE`; internal
errors → `SYSTEM`; not found, conflict, invalid state, quota → `BUSINESS`. The
message is kept only for `BUSINESS` / `VALIDATION` (client-safe text);
`reference` is the trace id.

### 8.5 Verifying

1. Write with `X-User-Id`, note `meta.traceId` in the response.
2. `kafka-console-consumer.sh --bootstrap-server <host>:9092 --topic apargo.audit.event --from-beginning --property print.key=true --property print.headers=true`
3. Expect one event: key = `eventId`, `traceId` = `meta.traceId`, actor `USER`.
4. Without Kafka locally: `AUDIT_ENABLED=false` (`LOG_LEVEL_AUDIT=DEBUG` prints each event).

| Log | Cause |
|---|---|
| `Bootstrap broker … disconnected` | Kafka unreachable: check `KAFKA_BOOTSTRAP_SERVERS`, firewall |
| `Topic … not present in metadata` | Broker unreachable, or topic missing with auto-create off |
| `Audit event not published: invalid` | Bug in `StorageAuditEvents`; the log lists each problem |
| `Audit event not published: publisher queue full` | Sustained Kafka slowness; raise `AUDIT_PUBLISHER_QUEUE_CAPACITY` or fix the broker |
| Actor `SERVICE/unknown` | The request had no `X-User-Id` |
