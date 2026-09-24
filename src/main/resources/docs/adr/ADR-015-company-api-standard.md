# ADR-015 — Adopt the company API Standard

**Status:** Accepted · 2026-09-24
**Supersedes:** the "FROZEN" response envelope in 04 §2, and the `X-Api-Key` /
`Idempotency-Key` / `X-Trace-Id` header names.

## Context

The company API Standard fixes the parts every API shares: headers, request
format, response wrapper, status codes, pagination and error format. This
service predates it and diverged on each:

| Area | Before | Standard |
|---|---|---|
| Wrapper | `{status: "SUCCESS"|"ERROR", message, data, error{code,message,details}, traceId}` | `{success, status (= HTTP), code, message, data, errors[], meta{requestId, timestamp, path}}` |
| Tracking | `X-Trace-Id` **and** `X-Request-Id` | `X-Request-Id` only |
| Service auth | `X-Api-Key` | `X-Internal-Api-Key` + `X-Internal-Caller` |
| Idempotency | `Idempotency-Key`, optional | `X-Idempotency-Key`, required on create |
| Batch routes | `207` | `200` with per-item results in `data` |
| Lists | `{items, nextCursor, hasMore}`, `limit` | `{items, pagination{size, nextCursor, hasNext}}`, `size` |
| Validation | `400 REQUEST_INVALID` for everything | `400 BAD_REQUEST` (unreadable) vs `422 VALIDATION_FAILED` (invalid fields) |

The mismatch was not only cosmetic: template-service already sent
`X-Internal-Api-Key`, which this service ignored, so the integration worked only
with authentication switched off.

The platform is in development, so the cost of changing now is the lowest it
will ever be.

## Decision

1. Every JSON response uses the standard wrapper (`ApiResponse`), built only
   through `Responses` (controllers), `GlobalExceptionHandler` and
   `ErrorResponseWriter` (filters). `status` always equals the HTTP status.
2. `ErrorCode` carries its HTTP status; a `DomainException` names its code and
   the status follows. Generic codes use the standard vocabulary
   (`BAD_REQUEST`, `FORBIDDEN`, `VALIDATION_FAILED`, `DEPENDENCY_FAILURE`, …);
   resource codes keep `<RESOURCE>_<PROBLEM>` names.
3. Per-file batch codes that template-service branches on are **not** renamed
   (`QUOTA_EXCEEDED`, `QUOTA_NOT_PROVISIONED`, `CONTENT_TYPE_NOT_ALLOWED`,
   `CONTENT_TYPE_MISMATCH`, `MEDIA_TOO_LARGE`, `BATCH_ITEM_SKIPPED`,
   `INTERNAL_ERROR`), and the `data` shape of the batch upload is unchanged.
4. Standard header names only, fixed in `HeaderNames` and not configurable.
   **No aliases** for `X-Api-Key`, `Idempotency-Key` or `X-Trace-Id`:
   template-service is the only caller and adopts the standard in the same
   release, so a compatibility window would be code with no user.
5. `X-Idempotency-Key` is required on upload, batch upload and initiate
   (`api.idempotency-key-required`).
6. `507` is kept for `QUOTA_EXCEEDED` — the literal HTTP meaning, already
   handled by consumers — and `CONTENT_TYPE_NOT_ALLOWED` stays `415`.

## Consequences

- One client function handles storage-service and template-service alike.
- Consumers must switch success checks from `status == "SUCCESS"` to the HTTP
  status or `success`. template-service reads only the standard shape, so the
  two services are deployed together.
- `security.api-key-header` and the unversioned `/quota` route guard are
  removed.
- Renamed codes: `REQUEST_INVALID` → `BAD_REQUEST`/`VALIDATION_FAILED`,
  `ACCESS_DENIED` → `FORBIDDEN`, `REQUEST_IN_PROGRESS` →
  `IDEMPOTENCY_KEY_IN_PROGRESS`, `DEPENDENCY_UNAVAILABLE` → `DEPENDENCY_FAILURE`.
- Status changes: `MEDIA_INVALID` 400→422, `BATCH_FILES_REQUIRED` 400→422,
  `QUOTA_NOT_PROVISIONED` 400→409, `IDEMPOTENCY_KEY_REUSED` 422→409,
  `QUOTA_LIMIT_INVALID` 500→422 (was unmapped), insufficient scope on
  `/internal` 401→403, missing/invalid tenant headers 401→400.
- `ErrorResponseWriter` no longer calls `response.reset()`, which had been
  wiping `Retry-After`/`X-RateLimit-*` from 429s.

## Alternatives rejected

- **Keep the frozen envelope, add a v2 path.** Doubles every controller for a
  service with a handful of internal consumers still in development.
- **Accept old header names for a migration window.** Built, then removed: the
  only caller changes in the same release, so the aliases would be permanent
  legacy with nobody using them.
- **Emit both envelopes.** No single JSON shape satisfies both `status:
  "SUCCESS"` and `status: 200`; a content-negotiated dual format is complexity
  with no end date.
