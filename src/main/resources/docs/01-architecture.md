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
