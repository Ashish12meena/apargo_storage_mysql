# 04 — API Contracts and Compatibility

**This document is the contract.** Controllers implement it; they do not define it.
A route that exists in code but not here is a defect.

## 1. Conventions

This service follows the **company API Standard** (ADR-015): headers, request
format, response wrapper, status codes, pagination and error format.

- Base path `/api/v1`. Internal service-to-service under `/internal`.
- Every JSON response uses the wrapper in §2 — including responses written by
  servlet filters and the container `/error` path. Documented exceptions: `204`
  (no body) and the file stream of `GET /api/v1/media/serve/**`.
- Timestamps ISO-8601 UTC. Sizes in bytes. Ids are strings on the wire.
- Field names `camelCase`; enum values `UPPER_SNAKE_CASE`.

**Request headers**

| Header | Required | Purpose |
|---|---|---|
| `X-Internal-Api-Key` | Yes | Calling service's key |
| `X-Internal-Caller` | Recommended | Calling service name; mismatches with the key's client are logged |
| `X-Org-Id`, `X-Project-Id` | Yes on `/api/**` | Tenant the request acts for; read only after the key validates. Missing/invalid → `400 BAD_REQUEST` |
| `X-User-Id` | When a user is acting | Logged (MDC `userId`); not used for authorisation |
| `X-Request-Id` | No | The only tracking header; generated if absent, always echoed. `X-Trace-Id` is not used |
| `X-Idempotency-Key` | Yes on create | Upload, batch upload, initiate. Missing → `400 IDEMPOTENCY_KEY_REQUIRED` |

**Response headers:** `Content-Type: application/json`, `X-Request-Id` (always),
`Location` (on `201`), `Retry-After` (on `429`, `503`, and `409
IDEMPOTENCY_KEY_IN_PROGRESS`).

**Only standard header names are accepted.** The pre-standard `X-Api-Key`,
`Idempotency-Key` and `X-Trace-Id` are not read at all: a request that uses
them gets `401` / `400 IDEMPOTENCY_KEY_REQUIRED` exactly as if the header were
absent.

**No endpoint on the tenant-facing surface accepts an org or project id** as a
path or query parameter. The internal admin surface (§4) addresses a *target*
tenant as resource identity; that is the one documented exception.

## 2. Response wrapper

```jsonc
// success
{
  "success": true,
  "status":  201,                    // always equals the HTTP status
  "code":    "SUCCESS",
  "message": "Upload complete",      // human readable, may be reworded freely
  "data":    { },                    // lists: { "items": [...], "pagination": {...} }
  "errors":  [],
  "meta":    { "requestId": "3f2a…", "timestamp": "2026-01-15T10:30:00Z" }
}
// error
{
  "success": false,
  "status":  422,
  "code":    "VALIDATION_FAILED",
  "message": "Request has invalid fields.",
  "data":    null,
  "errors":  [ { "field": "size", "code": "OUT_OF_RANGE", "message": "must be less than or equal to 100" } ],
  "meta":    { "requestId": "3f2a…", "timestamp": "2026-01-15T10:30:00Z", "path": "/api/v1/media" }
}
```

**Clients decide success from the HTTP status (or `success`), and use `code`
only for specific handling.** `errors[].code` is one of `REQUIRED`,
`INVALID_FORMAT`, `INVALID_VALUE`, `TOO_LONG`, `OUT_OF_RANGE`. Error codes are
listed in [10 §3](10-error-handling.md).

Pre-standard shape (`{status: "SUCCESS"|"ERROR", message, data, error, traceId}`)
is withdrawn — see §7.4.

## 3. Tenant-facing endpoints

### `POST /api/v1/media/upload` — proxied upload

Preserved from the current API. `multipart/form-data`, field `file`.

Headers: §1, including `X-Idempotency-Key` (required).

`201` + `Location: /api/v1/media/{id}` → `MediaResponse`.
Errors: `400` unreadable / missing key, `401`, `403`, `409` in progress or key
reused, `413` too large, `415` type not allowed, `422` invalid or content
mismatch, `429`, `507` quota exceeded.

Bodies above `media.validation.proxied-upload-threshold-bytes` are rejected with
`413` and a pointer to the direct-upload flow. Proxying large files is what makes
request duration a function of file size.

### `POST /api/v1/media/upload/batch` — NEW, batch proxied upload

Many small files in ONE request. Built for `template-service`, which downloads
media in batches and would otherwise open one connection per file.

```http
POST /api/v1/media/upload/batch
Content-Type: multipart/form-data
X-Internal-Api-Key, X-Internal-Caller, X-Org-Id, X-Project-Id, X-Request-Id
X-Idempotency-Key: <required, BATCH level>
```

- Multipart field name is **`files`**, repeated once per file. Not `file`.
- Maximum files per request: `storage.max-files-per-batch` (default 20).

**`200 OK`** whenever the batch was processed, including mixed results (the
standard's "action done, with a result"). Per-file outcomes are in `data`; read
`failedCount`, never infer per-file success from the HTTP status. (Was `207`
before ADR-015; every 2xx-accepting client is unaffected.)

```json
{
  "success": true, "status": 200, "code": "SUCCESS",
  "message": "Batch upload processed",
  "data": {
    "successCount": 2,
    "failedCount": 1,
    "results": [
      { "originalFilename": "a.jpg", "status": "SUCCESS", "url": "https://…", "mediaType": "IMAGE",
        "contentType": "image/jpeg", "fileSizeBytes": 1024, "error": null },
      { "originalFilename": "big.mp4", "status": "FAILED", "url": null, "mediaType": null,
        "contentType": null, "fileSizeBytes": null,
        "error": { "code": "MEDIA_TOO_LARGE", "message": "The file exceeds the maximum allowed size." } },
      { "originalFilename": "c.png", "status": "SUCCESS", "url": "https://…", "mediaType": "IMAGE",
        "contentType": "image/png", "fileSizeBytes": 2048, "error": null }
    ]
  },
  "errors": [],
  "meta": { "requestId": "…", "timestamp": "…" }
}
```

The `data` shape is **frozen for template-service**, which joins `results` to its
tasks by position and branches on `error.code`.

- `results` is in **request order**, one entry per submitted file, so
  `results.size() == successCount + failedCount` always holds.
- `originalFilename` is the filename supplied in the multipart part, verbatim.
  It is the join key clients use to match results to their own tasks — never a
  storage key, temp-file name, or normalised variant.
- `status` is `SUCCESS`, `FAILED`, or `SKIPPED`. **`SKIPPED` counts towards
  `failedCount`.**
- Failure entries carry a stable `errorCode` and a client-safe `message`. They
  never contain a storage key, filesystem path, provider error, or stack trace.

Every file traverses the same validation, inspection, quota reservation, storage
write and activation as `POST /media/upload` — because it *is* that path, called
in a loop, not a parallel implementation.

**Request-level rejections** are not batch results. They use the standard error
envelope through the global exception handler:

| Condition | Status | Code |
|---|---|---|
| `X-Idempotency-Key` absent | 400 | `IDEMPOTENCY_KEY_REQUIRED` |
| `files` absent or empty | 422 | `BATCH_FILES_REQUIRED` |
| more files than the configured maximum | 413 | `BATCH_TOO_MANY_FILES` |
| aggregate request over `max-request-size` | 413 | `MEDIA_TOO_LARGE` (container-level) |

**Storage-outage circuit.** After 3 consecutive files fail against the storage
backend, the remainder are marked `SKIPPED` with `BATCH_ITEM_SKIPPED` and not
attempted. Retrying a dead backend once per file turns one outage into thread
exhaustion.

**Idempotency is PER FILE, not batch-level.** A batch key of `K` produces
per-file keys `K:0`, `K:1`, … The index is used rather than the filename because
two files in one batch may legitimately share a name. A replayed batch re-serves
what completed and re-runs what did not, creating no duplicate records — but a
half-finished batch stays half-finished. See docs/17 A-12.

---

### `POST /api/v1/media/uploads` — initiate direct upload

Requires `X-Idempotency-Key`. `201` + `Location: /api/v1/media/uploads/{uploadId}`.

```jsonc
// request
{ "filename": "report.pdf", "declaredContentType": "application/pdf", "sizeBytes": 8388608 }
// 201
{ "uploadId": "...", "mediaId": "...", "mode": "PRESIGNED_SINGLE",
  "urls": ["https://..."], "requiredHeaders": { "Content-Type": "application/pdf" },
  "partSizeBytes": 0, "expiresAt": "2026-08-20T12:15:00Z" }
```

Reserves quota and creates a `PENDING` record. No bytes move. `requiredHeaders`
must be echoed verbatim on the PUT — they are part of the signature and constrain
the upload to the exact declared size and type, so a client cannot under-declare to
evade quota.

### `POST /api/v1/media/uploads/{uploadId}/complete`

```jsonc
{ "parts": [ { "partNumber": 1, "etag": "\"abc\"" } ] }   // multipart only
```

Verifies the object exists, matches the declared size, and inspects its leading
bytes. On mismatch the object is deleted, quota released, `422` returned. **Only
after this call is the media readable.** Idempotent: a repeat returns the same
result.

### `DELETE /api/v1/media/uploads/{uploadId}` — abandon

`204`. Releases quota, removes any partial object. Idempotent.

### `GET /api/v1/media` — list

**Cursor paging** (API Standard §5). Query: `type` (`IMAGE`/`VIDEO`/`AUDIO`/`DOCUMENT`),
`cursor` (omit for the first page), `size` (1–100, default 20). An out-of-range
`size` or unknown `type` is `422 VALIDATION_FAILED`.

```jsonc
"data": {
  "items": [ /* MediaResponse */ ],
  "pagination": { "size": 20, "nextCursor": "eyJ0IjoiMjAyNi...", "hasNext": true }
}
```

`nextCursor` is `null` and `hasNext` `false` on the last page. The cursor is
opaque — clients must not decode or build it. Cursor rather than page numbers
because offset paging degrades linearly with depth and the target is millions of
files; a total count would need a `COUNT(*)` on every request.

### `GET /api/v1/media/{mediaId}` — NEW

`200` → `MediaResponse`. `404` if absent **or** owned by another tenant — a `403`
would confirm existence and make the endpoint an enumeration oracle.

### `DELETE /api/v1/media/{mediaId}` — NEW

Query: `permanent` (default false; requires `media:delete:permanent`).

`204`. Idempotent — deleting an already-deleted item is `204`, not `404`. Quota is
released synchronously; the object is removed asynchronously.

### `POST /api/v1/media/{mediaId}/restore` — NEW

`200` → `MediaResponse`. `409` if past the grace period or already purged.

### `DELETE /api/v1/media/batch` — NEW

```jsonc
{ "mediaIds": ["1","2","3"] }
// 200 — data
{ "successCount": 1, "failedCount": 2,
  "results": [ { "id": "1", "success": true },
               { "id": "2", "success": false, "errorCode": "MEDIA_NOT_FOUND", "message": "Media not found." },
               { "id": "x", "success": false, "errorCode": "VALIDATION_FAILED", "message": "Malformed media id." } ] }
```

Max 100 ids. Partial success is normal; one failure never fails the batch.

### `GET /api/v1/media/{mediaId}/download-url` — NEW

Query: `ttlSeconds` (default 900, max 3600). `200` → `{ "url": ..., "expiresAt": ... }`.

**Replaces `GET /api/v1/media/public-url?storageKey=...`**, which accepts a
client-supplied storage key and performs no ownership check — any caller who learns
or guesses a key can mint a working URL for another tenant's file. The replacement
takes a media id and resolves the key server-side.

### `GET /api/v1/media/serve/**` — FROZEN PATH

Local provider only. Absolute URLs built from this path are persisted in another
service's database, so **the path may never move**. A documented exception to
the wrapper: success is the file (`200`/`206`/`304`); errors use the wrapper,
and a saturated stream pool is `503 SERVICE_UNAVAILABLE` + `Retry-After: 5`. Behaviour changes in Phase 3:
ownership check added, `Range` and `ETag` supported, `Cache-Control` becomes
`private, max-age=300`, `Content-Disposition: attachment` added.

### `GET /api/v1/quota` — NEW

`200` → `QuotaResponse` for the caller's own tenant. Today tenants cannot see their
usage and discover the limit by hitting it.

## 4. Internal endpoints

`/internal/**` requires an API key whose client holds `quota:admin` (no key →
`401 UNAUTHENTICATED`; key without the scope → `403 FORBIDDEN`), and is
network-restricted to the organisation service. Responses use the standard
wrapper. The org/project in these paths and bodies is the **target** of the
admin operation, not the caller's tenant — the documented exception to
"tenant only from headers". There is no flag that disables it
beyond `security.api-key-enabled`, which production rejects.

| Endpoint | Contract |
|---|---|
| `PUT /internal/quota/org` | Unchanged shape. Idempotent upsert. |
| `PUT /internal/quota/project` | Unchanged shape. Requires the org row to exist. |
| `GET /internal/quota/org/{orgId}` | Unchanged. |
| `GET /internal/quota/project/{orgId}/{projectId}` | Unchanged. |
| `DELETE /internal/media/project/{orgId}/{projectId}` | Async teardown. `202 Accepted` + `{jobId, statusUrl}`. `?permanent=` skips the grace period. Requires `tenant:teardown`. |
| `DELETE /internal/media/org/{orgId}` | Async offboarding, every project. Same contract. |

**Teardown response** (`202`):

```jsonc
{ "success": true, "status": 202, "code": "SUCCESS",
  "message": "Teardown accepted; processing asynchronously",
  "data": { "jobId": "6f1c...", "statusUrl": "/internal/quota/org/42", "scope": "ORG", "permanent": false },
  "errors": [], "meta": { "requestId": "...", "timestamp": "..." } }
```

`202`, not `200`: the work is accepted, not performed. Reporting success before the
files are gone would be a claim a compliance auditor could act on. Track completion
by the `tenant.teardown.completed` event, the `jobId` in `media_audit`, or the
quota at `statusUrl` falling to zero.

Both are processed in bounded batches through the outbox, so a crash resumes rather
than restarting.

Payload shapes are preserved; the wrapper follows §2. Coordinated with the
organisation service team ([08 §2](08-integrations.md)).

## 5. Status codes

| Code | Meaning |
|---|---|
| 200 | Read; action with a result — including batch upload/delete with mixed per-item results |
| 201 | Created (`upload`, `uploads`) + `Location` |
| 202 | Accepted; async work queued (teardown) — `{jobId, statusUrl}` |
| 204 | Done, no body (delete, abort) |
| 400 | Request can't be read: bad JSON, missing/invalid header (incl. tenant headers), missing `X-Idempotency-Key`, malformed id |
| 401 | Missing or invalid credential |
| 403 | Authenticated, scope missing |
| 404 | Absent **or** belongs to another tenant; unknown route |
| 409 | State conflict; quota not provisioned; idempotency key in progress or reused |
| 413 | File or batch too large |
| 415 | File type not allowed |
| 422 | Invalid field values (`VALIDATION_FAILED`, with `errors[]`); invalid media; content does not match declaration |
| 429 | Rate limited (`Retry-After`) |
| 500 | Unexpected |
| 501 | Operation not supported by the active provider |
| 502 | Storage backend failed |
| 503 | Temporarily saturated (`Retry-After`) |
| 507 | Quota exceeded — kept: the literal HTTP meaning, already handled by consumers |

`207` is no longer used.

## 6. Compatibility commitments

| Contract | Commitment |
|---|---|
| `/api/v1/media/serve/**` | Frozen forever. URLs persisted downstream. |
| Response wrapper | Company API Standard (ADR-015). Additive fields inside `data` only. |
| Batch upload `data` shape and per-file codes | Frozen — template-service parses them. |
| `POST /api/v1/media/upload` | Request shape unchanged. |
| Internal quota shapes | Unchanged. |
| `507` for quota | Preserved. |
| `ErrorCode` values | Append-only from ADR-015 on (four generic codes were renamed by it). |

## 7. Breaking changes and their migration

Breaking changes are acceptable where they buy something real. Three are planned.

**7.1 Authentication becomes mandatory.** Every request now needs an API key (header renamed to `X-Internal-Api-Key` by §7.4; originally `X-Api-Key`)
(ADR-010). Tenant headers are unchanged in name and meaning, so the only client
change is adding one header.

| Stage | Duration | Behaviour |
|---|---|---|
| 1 | — | Issue a key to each calling service; they add the header and deploy |
| 2 | — | `security.api-key-enabled: true` in staging; verify no caller 401s |
| 3 | — | Enable in production |

Simpler than the withdrawn JWT plan because the tenant contract does not change —
only the requirement to authenticate. Rejections are counted by
`storage.auth.rejected{reason}` and logged with the caller's IP, so a caller that
has not migrated is visible before the switch.

**7.2 Keyset pagination.** `page`/`size` continue to work through Phase 3, mapped
internally onto keyset with a `Deprecation` header, then removed.

**7.3 Type-specific listing routes.** `/images`, `/videos`, `/documents`, `/audio`
become aliases for `GET /api/v1/media?type=…` in Phase 2 and are sunset after
Phase 3.

**7.4 Company API Standard (ADR-015).** One coordinated change:

| Change | Client action |
|---|---|
| Wrapper `{success, status, code, message, data, errors, meta}` | Check HTTP status / `success`, not `status == "SUCCESS"`; read `code` instead of `error.code`, `errors` instead of `error.details`, `meta.requestId` instead of `traceId` |
| `X-Api-Key` → `X-Internal-Api-Key` | Rename the header (old name rejected) |
| `Idempotency-Key` → `X-Idempotency-Key`, now required on creates | Rename and always send (old name ignored) |
| `X-Trace-Id` removed | Use `X-Request-Id` |
| Batch routes `207` → `200`; batch delete `data` is an object | Treat any 2xx as processed; read `data.results` |
| List `limit` → `size`; `{nextCursor, hasMore}` → `pagination{size, nextCursor, hasNext}` | Rename |

No compatibility window: template-service is the only caller and moves to the
standard in the same release. **Deploy both together.** The unversioned legacy
`/quota` route guard is removed as well (no controller served it).

## 8. Versioning

`/v1` in the path, plus the tooling to actually run a transition — which today's
service has none of:

- `Deprecation: true` and `Sunset: <RFC 1123 date>` on deprecated routes.
- `Warning` naming the replacement.
- Per-route, per-consumer usage metrics, so sunset decisions are evidence-based
  rather than hopeful.
- Minimum 6-month overlap. `v1` and `v2` controllers coexist over a shared
  application layer.

OpenAPI is published as a CI artifact so consumers can generate clients and run
contract tests against it.
