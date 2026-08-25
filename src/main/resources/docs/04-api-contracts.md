# 04 — API Contracts and Compatibility

**This document is the contract.** Controllers implement it; they do not define it.
A route that exists in code but not here is a defect.

## 1. Conventions

- Base path `/api/v1`. Internal service-to-service under `/internal`.
- Every response uses the envelope in §2. **No exceptions**, including responses
  written by servlet filters before the dispatcher runs.
- All timestamps ISO-8601 UTC. All sizes in bytes. All ids are strings on the wire
  even when numeric internally, so a future id-format change is not breaking.
- Every mutating endpoint requires `Idempotency-Key`.
- Tenant scope comes from `X-Org-Id` / `X-Project-Id`, read only after the API key
  validates. **No endpoint accepts an org or project id as a path or query
  parameter** on the tenant-facing surface.

## 2. Response envelope — FROZEN

```jsonc
{
  "status":  "SUCCESS" | "ERROR",
  "message": "human readable, may be reworded freely",
  "data":    { },
  "error":   { "code": "QUOTA_EXCEEDED", "message": "...", "details": [ ] },
  "traceId": "0af7651916cd43dd8448eb211c80319c"
}
```

`status`, `message`, `data` are unchanged from the current service. `error` and
`traceId` are **additive** and therefore backward-compatible.

**Clients must branch on `error.code`, never on `message`.** Codes are append-only
within a major version: never renamed, never repurposed, never removed. Today
clients have only the message to match on, which breaks silently whenever anyone
improves the wording.

## 3. Tenant-facing endpoints

### `POST /api/v1/media/upload` — proxied upload

Preserved from the current API. `multipart/form-data`, field `file`.

Headers: `X-Api-Key` (required), `X-Org-Id`, `X-Project-Id` (required),
`Idempotency-Key` (recommended; required from Phase 3).

`201` → `MediaResponse`.
Errors: `400` invalid, `401`, `403`, `409` duplicate in progress, `413` too large,
`415` type not allowed, `422` content mismatch, `429`, `507` quota exceeded.

Bodies above `media.validation.proxied-upload-threshold-bytes` are rejected with
`413` and a pointer to the direct-upload flow. Proxying large files is what makes
request duration a function of file size.

### `POST /api/v1/media/upload/batch` — NEW, batch proxied upload

Many small files in ONE request. Built for `template-service`, which downloads
media in batches and would otherwise open one connection per file.

```http
POST /api/v1/media/upload/batch
Content-Type: multipart/form-data
X-Api-Key, X-Org-Id, X-Project-Id
Idempotency-Key: <optional, BATCH level>
```

- Multipart field name is **`files`**, repeated once per file. Not `file`.
- Maximum files per request: `storage.max-files-per-batch` (default 20).

**`207 Multi-Status`** on every response, including all-success — matching
`DELETE /api/v1/media/batch`, the service's other partial-success route.

```json
{
  "status": "SUCCESS",
  "message": "Batch upload processed",
  "data": {
    "successCount": 2,
    "failedCount": 1,
    "results": [
      { "originalFilename": "a.jpg", "status": "SUCCESS", "media": { "id": "41", "...": "..." } },
      { "originalFilename": "big.mp4", "status": "FAILED",
        "errorCode": "MEDIA_TOO_LARGE", "message": "The file exceeds the maximum permitted size." },
      { "originalFilename": "c.png", "status": "SUCCESS", "media": { "id": "42", "...": "..." } }
    ]
  },
  "traceId": "..."
}
```

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
| `files` absent or empty | 400 | `BATCH_FILES_REQUIRED` |
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

Query: `type`, `cursor`, `limit` (default 20, max 100).

```jsonc
{ "items": [ /* MediaResponse */ ], "nextCursor": "eyJ0IjoiMjAyNi...", "hasMore": true }
```

**BREAKING** relative to today's `page`/`size` + `Page<T>`. Offset pagination
degrades linearly with depth and the target is millions of files. Migration in §7.

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
// 207
{ "items": [ { "id": "1", "success": true },
             { "id": "2", "success": false, "errorCode": "MEDIA_NOT_FOUND" } ] }
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
service's database, so **the path may never move**. Behaviour changes in Phase 3:
ownership check added, `Range` and `ETag` supported, `Cache-Control` becomes
`private, max-age=300`, `Content-Disposition: attachment` added.

### `GET /api/v1/quota` — NEW

`200` → `QuotaResponse` for the caller's own tenant. Today tenants cannot see their
usage and discover the limit by hitting it.

## 4. Internal endpoints

`/internal/**` requires an API key whose client holds `quota:admin`, and is
network-restricted to the organisation service. There is no flag that disables it
beyond `security.api-key-enabled`, which production rejects.

| Endpoint | Contract |
|---|---|
| `PUT /internal/quota/org` | Unchanged shape. Idempotent upsert. |
| `PUT /internal/quota/project` | Unchanged shape. Requires the org row to exist. |
| `GET /internal/quota/org/{orgId}` | Unchanged. |
| `GET /internal/quota/project/{orgId}/{projectId}` | Unchanged. |
| `DELETE /internal/media/project/{orgId}/{projectId}` | Async teardown. `202 Accepted` + handle. `?permanent=` skips the grace period. Requires `tenant:teardown`. |
| `DELETE /internal/media/org/{orgId}` | Async offboarding, every project. Same contract. |

**Teardown response** (`202`):

```jsonc
{ "status": "SUCCESS", "message": "Teardown accepted; processing asynchronously",
  "data": { "handle": "6f1c...", "scope": "ORG", "permanent": false, "status": "ACCEPTED" },
  "traceId": "..." }
```

`202`, not `200`: the work is accepted, not performed. Reporting success before the
files are gone would be a claim a compliance auditor could act on. Track completion
by the `tenant.teardown.completed` event or the `handle` in `media_audit`.

Both are processed in bounded batches through the outbox, so a crash resumes rather
than restarting.

Request and response shapes are preserved exactly. Only the authentication
requirement changes, and that is coordinated with the organisation service team
([08 §2](08-integrations.md)).

## 5. Status codes

| Code | Meaning |
|---|---|
| 200 / 201 / 204 | Success |
| 202 | Accepted; async work queued (teardown) |
| 207 | Batch, mixed results — `DELETE /media/batch` and `POST /media/upload/batch` |
| 400 | Malformed, or quota not provisioned |
| 401 | Missing or invalid credential |
| 403 | Authenticated, scope missing |
| 404 | Absent **or** belongs to another tenant |
| 409 | State conflict, or an identical request in progress |
| 413 | Body exceeds the proxied-upload limit |
| 415 | Declared type not allowed |
| 422 | Content does not match declaration; or idempotency key reused for a different request |
| 429 | Rate limited |
| 500 | Unexpected |
| 502 | Storage backend failed |
| 503 | Dependency unavailable |
| 507 | Quota exceeded — **preserved deliberately** for downstream compatibility |

## 6. Compatibility commitments

| Contract | Commitment |
|---|---|
| `/api/v1/media/serve/**` | Frozen forever. URLs persisted downstream. |
| Response envelope | Frozen. Additive fields only. |
| `POST /api/v1/media/upload` | Request shape unchanged. |
| Internal quota shapes | Unchanged. |
| `507` for quota | Preserved. |
| `ErrorCode` values | Append-only. |

## 7. Breaking changes and their migration

Breaking changes are acceptable where they buy something real. Three are planned.

**7.1 Authentication becomes mandatory.** Every request now needs `X-Api-Key`
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
