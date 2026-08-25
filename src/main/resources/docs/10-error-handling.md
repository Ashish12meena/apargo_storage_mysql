# 10 — Error Handling Strategy

## 1. Principles

1. **Every error carries a stable code.** Clients branch on `error.code`, never on
   `message`. Today only the message exists, so any wording improvement silently
   breaks a consumer.
2. **Internal detail never reaches the client.** The current handler returns
   `ex.getMessage()` directly for storage and not-found errors, and those messages
   embed storage keys.
3. **Every error carries a `traceId`**, so a caller can quote one identifier
   instead of describing a timestamp.
4. **The status code is chosen at the boundary.** The domain does not know HTTP.
5. **One envelope, everywhere** — including from servlet filters that run outside
   the dispatcher.
6. **Log once, at the boundary.** Logging at every level produces one incident as
   five stack traces.

## 2. Taxonomy

```
DomainException  (abstract, carries ErrorCode)
├── MediaNotFoundException          404
├── TenantAccessDeniedException     403
├── InvalidMediaException           400
├── ContentTypeMismatchException    422
├── QuotaExceededException          507
├── QuotaNotProvisionedException    400
├── IllegalMediaStateException      409
└── UploadSessionExpiredException   409

InfrastructureException  (abstract)
├── StorageUnavailableException     502
├── DependencyUnavailableException  503
└── LockAcquisitionException        503
```

`getMessage()` is for logs and may contain internal detail.
`clientMessage()` is what reaches the caller and must contain none.

## 3. Mapping

| Exception | Status | Code | Logged at |
|---|---|---|---|
| `InvalidMediaException` | 400 | `MEDIA_INVALID` | DEBUG |
| `MethodArgumentNotValidException` | 400 | `REQUEST_INVALID` | DEBUG |
| `QuotaNotProvisionedException` | 400 | `QUOTA_NOT_PROVISIONED` | INFO |
| *(no credential)* | 401 | `UNAUTHENTICATED` | INFO |
| `TenantAccessDeniedException` | 403 | `ACCESS_DENIED` | **WARN** |
| `MediaNotFoundException` | 404 | `MEDIA_NOT_FOUND` | DEBUG |
| `IllegalMediaStateException` | 409 | `MEDIA_ILLEGAL_STATE` | INFO |
| *(idempotent request in flight)* | 409 | `REQUEST_IN_PROGRESS` | DEBUG |
| `UploadSessionExpiredException` | 409 | `UPLOAD_SESSION_EXPIRED` | INFO |
| `MaxUploadSizeExceededException` | 413 | `MEDIA_TOO_LARGE` | DEBUG |
| *(type not allowed)* | 415 | `CONTENT_TYPE_NOT_ALLOWED` | INFO |
| `ContentTypeMismatchException` | 422 | `CONTENT_TYPE_MISMATCH` | **WARN** |
| *(idempotency key reused)* | 422 | `IDEMPOTENCY_KEY_REUSED` | WARN |
| *(rate limited)* | 429 | `RATE_LIMITED` | INFO |
| `QuotaExceededException` | **507** | `QUOTA_EXCEEDED` | INFO |
| `StorageUnavailableException` | 502 | `STORAGE_UNAVAILABLE` | **ERROR** |
| `DependencyUnavailableException` | 503 | `DEPENDENCY_UNAVAILABLE` | **ERROR** |
| Anything else | 500 | `INTERNAL_ERROR` | **ERROR** + stack |

**507 for quota is preserved deliberately** from the current service for downstream
compatibility, despite 429 arguably being more conventional. Changing it would
break a consumer for a cosmetic gain.

**403 and 422 are logged at WARN** because both indicate either an attack or a
broken client, and both are worth alerting on above a rate threshold.

## 4. Gaps in the current handler

| Gap | Consequence |
|---|---|
| No handler for `MethodArgumentNotValidException` | Bean-validation failures become 500s |
| No handler for `NumberFormatException` from the context interceptor | A non-numeric `X-Org-Id` is a 500, not a 400 |
| `handleStorage` returns `ex.getMessage()` | Storage keys and provider detail reach the client |
| `MediaNotFoundException("File not found: " + storageKey)` | Leaks the key in a 404 body |
| No `traceId` anywhere | A caller reporting a failure has nothing to quote |
| Filters emit hand-built JSON | Rate-limit responses match the envelope by coincidence and will drift |

## 5. Retry guidance

| Status | Client should | Header |
|---|---|---|
| 400, 403, 404, 413, 415, 422 | **Not** retry — retrying is deterministic failure | — |
| 409 `REQUEST_IN_PROGRESS` | Retry after the hint | `Retry-After` |
| 429 | Back off | `Retry-After` |
| 500, 502, 503 | Retry with exponential backoff and jitter | `Retry-After` when known |
| 507 | Not retry until space is freed | — |

Documented explicitly because a client retrying a 422 forever is a common and
avoidable failure, and one this service will see from `template-service`.

## 6. Partial failure

Batch endpoints return `207` with per-item results. One bad item never fails the
batch — the current batch upload endpoint already does this well, and the pattern is
kept.

## 7. Degradation

| Failure | Behaviour |
|---|---|
| Redis down | Rate limiting **fails open**, alert raised |
| S3 degraded | Uploads 503 with `Retry-After`; CDN reads unaffected |
| MySQL down | 503; readiness fails; pod leaves the load balancer but is **not** killed |
| Outbox backlog | Alert; API unaffected |
| Unknown API key presented | 401, counted by `storage.auth.rejected{reason}`; alert above a rate threshold |

**Liveness and readiness are distinct.** A pod that cannot reach S3 must leave the
load balancer, not be restarted into the same failure.
