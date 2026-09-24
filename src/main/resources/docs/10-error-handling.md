# 10 — Error Handling Strategy

## 1. Principles

1. **HTTP status first, then `code`.** Every error uses the standard wrapper
   (`success: false`, `data: null`), and `status` always equals the HTTP status.
   Clients branch on `code` only for specific handling, never on `message`.
2. **The code decides the status.** Each `ErrorCode` carries its HTTP status, so
   a `DomainException` names a code and nothing else; there is no second table
   that can disagree.
3. **Internal detail never reaches the client.** `getMessage()` is for logs;
   `clientMessage()` (the code's default text) is what the caller sees.
4. **Every error carries `meta.requestId`**, equal to the `X-Request-Id` header.
5. **One wrapper, everywhere** — controllers, servlet filters
   (`ErrorResponseWriter`), and the container `/error` path (`ApiErrorController`).
6. **Log once, at the boundary.**

## 2. Taxonomy

```
DomainException  (abstract, carries ErrorCode → HTTP status)
├── InvalidMediaException              422 MEDIA_INVALID
├── ContentTypeMismatchException       422 CONTENT_TYPE_MISMATCH
├── ContentTypeNotAllowedException     415 CONTENT_TYPE_NOT_ALLOWED
├── MediaTooLargeException             413 MEDIA_TOO_LARGE
├── BatchTooLargeException             413 BATCH_TOO_MANY_FILES
├── InvalidBatchException              422 BATCH_FILES_REQUIRED
├── MediaNotFoundException             404 MEDIA_NOT_FOUND
├── UploadSessionNotFoundException     404 UPLOAD_SESSION_NOT_FOUND
├── TenantAccessDeniedException        403 FORBIDDEN
├── IllegalMediaStateException         409 MEDIA_ILLEGAL_STATE
├── UploadSessionExpiredException      409 UPLOAD_SESSION_EXPIRED
├── RequestInProgressException         409 IDEMPOTENCY_KEY_IN_PROGRESS  (+ Retry-After: 2)
├── IdempotencyConflictException       409 IDEMPOTENCY_KEY_REUSED
├── IdempotencyKeyRequiredException    400 IDEMPOTENCY_KEY_REQUIRED
├── QuotaNotProvisionedException       409 QUOTA_NOT_PROVISIONED
├── InvalidQuotaLimitException         422 QUOTA_LIMIT_INVALID
├── QuotaExceededException             507 QUOTA_EXCEEDED
├── StorageOperationException          502 STORAGE_UNAVAILABLE
├── UnsupportedStorageOperationException 501 OPERATION_UNSUPPORTED
└── ServiceBusyException               503 SERVICE_UNAVAILABLE          (+ Retry-After: 5)
```

## 3. Mapping

| Situation | Status | `code` | `errors[]` | Logged at |
|---|---|---|---|---|
| Unparseable JSON, missing/invalid header, malformed id, missing tenant headers | 400 | `BAD_REQUEST` | – | WARN / ERROR (raw `IllegalArgumentException`) |
| No `X-Idempotency-Key` on a create | 400 | `IDEMPOTENCY_KEY_REQUIRED` | – | INFO |
| No or unknown API key | 401 | `UNAUTHENTICATED` | – | WARN + `storage.auth.rejected` |
| Scope missing | 403 | `FORBIDDEN` | – | **WARN** |
| Unknown route | 404 | `NOT_FOUND` | – | DEBUG |
| Wrong method | 405 | `METHOD_NOT_ALLOWED` (+ `Allow`) | – | – |
| Wrong request `Content-Type` | 415 | `UNSUPPORTED_MEDIA_TYPE` | – | – |
| Bean validation (body, query, path), missing param/part, bad enum/type in query or body | 422 | `VALIDATION_FAILED` | `REQUIRED`, `INVALID_VALUE`, `OUT_OF_RANGE`, `TOO_LONG`, `INVALID_FORMAT` | DEBUG |
| Rate limited (filter) | 429 | `RATE_LIMITED` (+ `Retry-After`) | – | INFO |
| Container multipart ceiling | 413 | `MEDIA_TOO_LARGE` | – | DEBUG |
| Domain exceptions | per §2 | per §2 | – | INFO; **WARN** for `FORBIDDEN`, `CONTENT_TYPE_MISMATCH`, `IDEMPOTENCY_KEY_REUSED`; **ERROR** for 5xx |
| Anything else | 500 | `INTERNAL_ERROR` | – | **ERROR** + stack |

**507 for quota is kept**: it is the literal HTTP meaning, consumers already
handle it, and a 5xx that must *not* be retried is called out in §5.

## 4. Gaps closed

| Gap | Fixed by |
|---|---|
| Two tracking ids (`X-Trace-Id`, `X-Request-Id`) | `RequestIdFilter`: `X-Request-Id` only (ADR-015) |
| Filter errors lost `Retry-After` / rate-limit headers (`response.reset()`) | `ErrorResponseWriter` resets the body buffer only |
| `InvalidQuotaLimitException` had no mapping → 500 | Status on `ErrorCode` (422) |
| Every client mistake was `400 REQUEST_INVALID` | 400 unreadable vs 422 invalid fields, with field codes |
| Container errors used Boot's default JSON | `ApiErrorController` |

## 5. Retry guidance

| Status | Client should | Header |
|---|---|---|
| 400, 401, 403, 404, 413, 415, 422 | **Not** retry — retrying is deterministic failure | — |
| 409 `IDEMPOTENCY_KEY_IN_PROGRESS` | Retry the same request after the hint | `Retry-After` |
| 409 other | Not retry without changing state | — |
| 429 | Back off | `Retry-After` |
| 500, 502, 503 | Retry with exponential backoff and jitter | `Retry-After` when known |
| 507 | Not retry until space is freed | — |

## 6. Partial failure

Batch endpoints return `200` with per-item results in `data`
(`successCount`, `failedCount`, `results[]`). One bad item never fails the batch;
clients read `failedCount`, not the HTTP status. Request-level problems (no key,
no files, too many files) are ordinary error responses.

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
