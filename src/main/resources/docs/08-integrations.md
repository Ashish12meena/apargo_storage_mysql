# 08 — Integrations

## 1. Inbound

### 1.1 Calling services → Storage Service

Every caller presents an `X-Api-Key` identifying itself, plus `X-Org-Id` and
`X-Project-Id` naming the tenant it is acting for (ADR-010).

| Item | Requirement |
|---|---|
| Header | `X-Api-Key: <key>` (header name configurable) |
| Key length | ≥32 characters, from a secret store |
| Tenant | `X-Org-Id`, `X-Project-Id` — read only after the key validates |
| Scopes | Configured per client, not sent by the caller |
| Rotation | Per client, 90 days: add new key → deploy caller → remove old |

**No gateway dependency.** The earlier design required the gateway to mint signed
JWTs, which was the critical-path blocker BL-1. That is withdrawn; this service now
authenticates callers itself.

### 1.2 `template-service` → Storage Service

Uploads media for message templates. Server-to-server.

- **Retries.** This is precisely why idempotency keys are mandatory: today a retry
  after a timeout creates a second file, a second row, and a second quota charge,
  and — with no delete endpoint — that quota is unreclaimable.
- Requires an API key with `media:read`, `media:write`, `media:delete`.
- **Persists absolute `/api/v1/media/serve/**` URLs in its own database**, which is
  why that route is frozen forever.

**Coordination required:** confirm whether `template-service` stores the URL, the
media id, or both. If only the URL, adding `mediaId` to its schema is a
prerequisite for the v2 response shape.

### 1.3 Organisation Service → Storage Service

Provisions quota via `/internal/quota/**`.

- Requires an API key with `quota:admin`. In the predecessor this was
  **unauthenticated** — one call zeroed any tenant's quota.
- Provisioning must happen **before** a project's first upload, or that upload
  fails with `QUOTA_NOT_PROVISIONED`. This is a real onboarding failure mode; see
  [17 R-4](17-risks-assumptions.md).

**Open question (OD-3):** quota *limits* arguably belong to the organisation
service, which knows the billing plan. We own *consumption*. The current split
means an out-of-band call must be remembered for every new project.

## 2. Outbound

### 2.1 S3 / CloudFront

Primary storage. **IRSA / instance role only — no static credentials.**
`accessKey` and `secretKey` are removed from the configuration schema entirely so
they cannot be set even deliberately.

Circuit breaker plus bounded retry on 5xx and throttling; no retry on 4xx.

### 2.2 Redis

Rate limiting only, plus presigned-URL caching. **Not a source of truth
for anything.** Unavailable → rate limiting fails open with an alert; quota and
metadata are untouched.

### 2.3 `waba-service` and Meta Graph API — to be removed

**Current:** `MediaUploadOrchestrator` calls `waba-service` and then the Meta Graph
API synchronously, inline, before returning to the caller, with a 60-second read
timeout. Upload latency and availability become a function of a third party. In the
wired upload service the same call is merely commented out — one uncomment from
returning.

**Phase 3:** moved behind the outbox. Upload latency decouples entirely.

**Recommended (ADR-009, OD-1):** remove it from this service altogether. We emit
`media.created`; `waba-service` subscribes and performs its own push, fetching bytes
by presigned URL. That deletes an entire integration package, the Meta client, WABA
credentials, the `X-Waba-Id` header, and a block of Facebook-specific resilience
configuration from a service that should only know about files.

The counter-argument — one extra network hop to fetch bytes — is weak: that hop is
S3-to-service inside the same region, and it buys a clean ownership boundary.

**Needs agreement from the WABA team.** Until then the transitional shim stands.

### 2.4 Eureka

Service discovery, opt-in via the `eureka` Maven profile. Registration means this
service is reachable from inside the mesh — which is exactly why an unauthenticated
header-trust model was unsafe, and why the API key is required before tenant
headers are read at all.

### 2.5 Organisation Service (outbound, periodic)

Existence check for org/project ids. `organisation_id` and `project_id` are
foreign-key-shaped columns with no foreign key, because the authoritative table
lives elsewhere. **Never called on the request path** — a synchronous dependency on
another service for every upload trades our availability for a weak guarantee. A
weekly job flags rows whose tenant no longer exists.

## 3. Integration principles

1. **No synchronous third-party call in a request path.** Behind the outbox, or not
   at all.
2. **No outbound call inside a database transaction.** It holds a connection for a
   remote operation's duration.
3. **Every outbound call has a timeout, a retry policy, and a circuit breaker.**
   Resilience4j is already configured for the Facebook and WABA clients — but in
   **two overlapping config blocks** that the config file's own comment admits must
   be hand-synced. Consolidate to one; two blocks kept in sync by convention will
   drift.
4. **Fail soft where the effect is not user-visible.** A failed WhatsApp push
   should not fail an upload — but it must land in a dead-letter queue, not a log
   line.
