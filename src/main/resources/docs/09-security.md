# 09 — Security Requirements

## 1. Threat model

| Threat | Current exposure | Control |
|---|---|---|
| Cross-tenant read/write by an unauthenticated caller | **Live in the predecessor.** | API key required before headers are read (§2) |
| Cross-tenant access by an *authenticated* caller | **Accepted risk.** The key does not bind the tenant. | `fixed-org-id` pinning; audit attribution (§2) |
| Cross-tenant read via leaked storage key | **Live.** Serve path performs no ownership check. | Key ownership check (§3.3) |
| Quota destruction via internal API | **Live in the predecessor.** `/internal/**` unauthenticated. | API key with `quota:admin` + NetworkPolicy (§4) |
| Enumeration of media ids | **Live.** Auto-increment ids, tenant-blind `deleteById`. | Tenant-scoped repositories, 404-not-403 (§3) |
| MIME-confusion / stored XSS | **Live.** Only the declared type is checked. | Magic-byte inspection (§5) |
| Credential leak | **Occurred.** Live AWS key and DB password committed. | Rotation + `gitleaks` gate (§7) |
| Quota exhaustion by retry | **Live.** No idempotency; no delete to reclaim. | Idempotency keys |
| Resource exhaustion via downloads | **Live.** Serve path is unthrottled beyond a default IP bucket. | Per-tenant limits + bounded concurrency |

## 2. Authentication

### Shared API key per calling service

```
caller ──X-Api-Key──▶ storage-service
                          │
              constant-time key lookup
                          │
         client identity + scopes  +  X-Org-Id / X-Project-Id
                          │
                    TenantPrincipal
```

Each calling service has its own key and its own scopes
(`security.clients[]`). The key says **who is calling**; the tenant headers say
**which tenant the call is for**, and are read only after the caller is
authenticated (ADR-010).

**Rules that admit no exception:**

1. Tenant headers are read **only after** a valid key is presented. An
   unauthenticated request never reaches the point where they are parsed.
2. Rejection happens in a servlet `Filter`, before the dispatcher. The predecessor
   used an interceptor — skipped on error dispatches — and explicitly **excluded**
   `/api/v1/media/serve/**` from it, so that route had no tenant context at all.
3. Keys are compared in constant time against every configured key. A map lookup
   leaks key material through timing.
4. Keys must be ≥32 characters, enforced at startup.
5. `api-key-enabled: false` is for local development. **Production startup fails**
   if it is false — a control that can be switched off in production is not a
   control.

### The limitation, stated plainly

**An authenticated caller may assert any tenant.** The key identifies the service,
not the tenant, so a compromised or buggy `template-service` can pass any
`X-Org-Id`. This is a confused deputy, and the API key model cannot prevent it.

What is available against it:

| Control | Effect |
|---|---|
| `security.clients[].fixed-org-id` | Pins a client to one org; headers ignored entirely. Removes the risk **completely** for that caller. |
| Startup warning | Every client NOT pinned is logged at boot, by name. |
| `media_audit.actor_id` | Every action attributed to a client id, so a misbehaving caller is identifiable afterwards. |

Closing it properly requires the tenant to be **cryptographically asserted by the
gateway** rather than claimed by the caller — the withdrawn JWT design in
ADR-010's History section. Reinstating it replaces one class.

### Service-to-service: `/internal/**`

Same key mechanism, plus a `quota:admin` scope requirement, plus a NetworkPolicy
restricting the route to the organisation-service pod selector. In the predecessor
this surface had **no authentication whatsoever**: one call set any tenant's quota
to zero.

## 3. Authorization and tenant isolation

Four independent layers. A defect in any one does not breach.

### 3.1 Scope check, before any lookup
`media:read`, `media:write`, `media:delete`, `media:delete:permanent`,
`quota:read`, `quota:admin`, `tenant:teardown`.

`tenant:teardown` is deliberately NOT implied by `media:delete`: deleting one file
and wiping a customer's entire library are different blast radii and should require
different credentials. Checked first, so a caller lacking the scope learns
nothing about whether the resource exists.

### 3.2 Tenant-scoped repositories — the load-bearing control

Repository interfaces **physically cannot express a tenant-blind query**. There is
no `findById(MediaId)` and no `deleteById(MediaId)`.

This is not a style preference. The current `MediaCommandService.deleteById(Long)`
takes no tenant scope; wiring it to a controller is a direct IDOR against an
auto-increment id. **If the method does not exist, the bug cannot be written.**
Enforced by ArchUnit, not by review.

### 3.3 Storage-key ownership

Every path resolving a key to bytes asserts the key starts with the caller's
`org-{n}/proj-{m}/` prefix. The current serve controller has only path-traversal
protection and relies entirely on UUIDs being unguessable — so a key leaked through
a log line, a `Referer` header, browser history, or a shared link is a cross-tenant
read.

**Storage keys are therefore treated as secrets** until this check exists: never
logged above DEBUG, never returned in an API response
([04 §3](04-api-contracts.md)).

### 3.4 404, never 403

Cross-tenant access returns `404`. A `403` confirms existence and turns the endpoint
into an enumeration oracle.

## 4. The internal surface

Currently has **no authentication of any kind**, and is outside the rate limiter
(registered on `/api/**` only). `PUT /internal/quota/org` with an org id and a byte
limit is a one-call denial of service against any tenant.

| Layer | Control |
|---|---|
| Network | Kubernetes NetworkPolicy — only the organisation-service pod selector, committed in this repo |
| Transport | TLS. mTLS optional; it authenticates the service but not the tenant |
| Application | API key whose client holds `quota:admin` |

Phase 0 ships the application layer immediately; the other two follow in Phase 1.

## 5. Content security

1. **Magic-byte inspection at ingest.** The declared type is a hint; the detected
   type is the truth and is what gets persisted and served.
2. **Reject on mismatch** — `422`. Never store with a corrected type silently: a
   mismatch is a signal, not a formatting problem.
3. **Allowlist, never denylist.** One list, in configuration. The current service
   has two lists in two files that already disagree.
4. `Content-Disposition: attachment` on every download, so no uploaded file renders
   inline in a browser origin. This is the practical mitigation for stored XSS via
   uploaded HTML or SVG.
5. `X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src
   'none'` on file responses.
6. **No structural validation** — no pixel counts, no page counts, no archive
   inspection. Nothing in this service decodes stored files, so a decompression
   bomb is inert here; the bound belongs in whatever component eventually renders
   them (ADR-013).
7. **Async malware scanning, wired but off by default** (ADR-014). Enable with
   `media.scanning.enabled` plus a real `provider`. Never in the request path.
   Startup fails if scanning is enabled with `provider: noop`.

## 6. Data protection

| Layer | Control |
|---|---|
| In transit | TLS 1.3 externally; mTLS internally |
| At rest (objects) | SSE-KMS with a customer-managed key, **asserted by the application**, not assumed from a bucket default |
| At rest (database) | Storage-level encryption |
| Backups | Encrypted, tested restore quarterly |

**Never logged:** API keys, presigned URLs (they contain signatures), storage keys
above DEBUG, original filenames above DEBUG, request bodies.

Note: `UserContextInterceptor` currently logs org and project at INFO on **every
request** — a per-request line of pure noise that also correlates tenants to
traffic patterns in any log aggregator.

## 7. Secret management

### Incident

`application-storage.yml` contains a live-format AWS access key and secret in
plaintext. `application-database.yml` contains a real DB password as a default.
This is at least the third recorded credential leak in this repository's lineage.

**Phase 0, before any other work:** deactivate the IAM key, audit CloudTrail for its
use, rotate the DB password, purge both from git history with `git filter-repo`,
force-push, re-clone every working copy. Removing the line in a new commit is not
sufficient — the secret remains in history and in every fork.

### Controls

1. **`gitleaks` as a pre-commit hook and a blocking CI gate.** This is the control
   that actually prevents recurrence. The previous leaks were not caused by
   carelessness that more care would fix.
2. **No secret has a default value in any configuration file.** A missing
   environment variable fails startup rather than booting with a fallback
   credential.
3. Secrets from AWS Secrets Manager or Vault, injected at runtime.
4. **No static AWS credentials.** IRSA. The fields are removed from the schema.
5. Rotation: DB 90 days, API keys 90 days, per client.
6. Actuator exposure narrowed in prod to `health,info,metrics,prometheus`. The
   current base config exposes `env`, `beans`, and `loggers` with
   `show-details: always`, which leaks bound configuration — including secrets — to
   anyone reaching the management port.

## 8. Rate limiting as a security control

Layered so one tenant cannot exhaust the service and one user cannot exhaust their
tenant: per-user, per-project, per-org, and per-IP for unauthenticated traffic. Plus
a **bandwidth** limit — fifty 500 MB uploads and fifty 5 KB uploads are the same
request count and vastly different load, and only request count is limited today.

Client IP resolution must respect a trusted-proxy list. The current implementation
takes `X-Forwarded-For` verbatim, so any caller can spoof a fresh bucket.

Fails **open** when Redis is unavailable, with an alert. Refusing all traffic
because the limiter is down converts a degradation into an outage.
