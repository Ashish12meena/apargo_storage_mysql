# ADR-010 — Authentication by shared API key per calling service

**Status:** Accepted · 2026-08-23 · **supersedes the JWT design, see History**

## Context

The predecessor derived tenant identity from `X-Org-Id` and `X-Project-Id`, read
verbatim with no verification of any kind. Anyone able to reach the port could set
those headers to another tenant's ids and read, upload as, and exhaust quota for
that tenant. Closing that is the reason this service is being rebuilt.

Every caller is an internal service: `template-service`, the chat system, and the
organisation service. There are no browser or end-user callers.

## Decision

**A shared API key per calling service.** Each client is configured with its own
key and its own scopes:

```yaml
security:
  api-key-enabled: true
  clients:
    - id: template-service
      key: ${TEMPLATE_SERVICE_API_KEY}
      scopes: [media:read, media:write, media:delete]
    - id: org-service
      key: ${ORG_SERVICE_API_KEY}
      scopes: [quota:admin, quota:read]
```

The key answers **who is calling**. The tenant headers answer **which tenant the
call is for**, and are read only after the caller is authenticated.

## What this does and does not give you

**Closes:** the predecessor's central defect. An arbitrary caller on the network
can no longer act as any tenant — they need a configured key first. Per-client
scopes mean a compromised `template-service` key cannot mutate quota. Per-client
keys mean one can be rotated or revoked without touching the others.

**Leaves open — state this plainly:** an authenticated caller may assert ANY
tenant. If `template-service` is compromised or buggy, it can pass any
`X-Org-Id`. This is the classic confused-deputy shape and the API key model cannot
prevent it, because the key says nothing about the tenant.

Mitigations available today:

- `security.clients[].fixed-org-id` pins a client to one organisation and ignores
  the headers entirely. For a single-tenant integration this removes the risk
  completely, and startup logs a warning for every client that is NOT pinned.
- Every request is attributed in `media_audit` by client id, so a misbehaving
  caller is identifiable after the fact.

**Other consequences:**

- A shared secret is a bearer credential: anyone who obtains it is that service.
  Keys must come from a secret store, never appear in a URL or a log line, and be
  rotated on a schedule.
- Keys must be at least 32 characters — enforced at startup, because the whole
  model rests on one value.
- Comparison is constant-time (`MessageDigest.isEqual`) against every configured
  key. A map lookup would be faster and would leak key material through timing.
- `api-key-enabled: false` exists for local development and **production startup
  fails if it is false**. A control that can be switched off in production is not
  a control.

## History — the superseded JWT design

The original decision was gateway-issued JWTs verified in-service against a JWKS
endpoint (RS256/ES256, `org_id`/`project_id`/`scope` claims). That design was
stronger: tenant identity was *cryptographically asserted by the gateway* rather
than claimed by the caller, which closes the confused-deputy case this decision
leaves open. It also carried per-user identity, making the audit trail answer
"who deleted this file" rather than only "which service".

It was withdrawn on the product owner's decision: it required coordinated work
from the gateway team (tracked as BL-1), and the operational cost was judged not
worth it for a service whose callers are all internal and trusted.

`ApiKeyAuthenticator` is deliberately the only class that knows how a caller is
identified. Reinstating JWT means replacing that one class — controllers,
services, `MediaAccessGuard` and the whole `TenantPrincipal` contract are
unchanged.

## Revisit when

- A caller becomes untrusted — a browser, a partner integration, a customer-run
  system.
- Per-user attribution is needed for compliance. An API key can only identify a
  service.
- The confused-deputy risk becomes concrete: a caller handles more than one
  tenant AND is exposed to untrusted input that could influence which tenant it
  names.

## Alternatives rejected

**Keep trusting headers with no authentication.** Exactly the predecessor. Any
caller on the network acts as any tenant.

**mTLS.** Stronger caller authentication than a shared secret, and it proves
*which service* called, not *which tenant* the call is for — so it leaves the same
gap while adding certificate lifecycle management.

**One global API key for all callers.** Simpler, and it makes rotation
all-or-nothing, prevents per-client scopes, and makes the audit trail unable to
say which service acted.
