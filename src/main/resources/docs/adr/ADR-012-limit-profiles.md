# ADR-012 — Per-file limits are global; only storage capacity is per-tenant

**Status:** Accepted · 2026-08-22 · **superseded an earlier draft, see History**

## Context

Two different things were being conflated:

| Concern | Varies per tenant? | Changes when? |
|---|---|---|
| **Per-file size limit** and MIME allowlist | No | With a release |
| **Total storage capacity** | Yes | Continuously, as tenants are onboarded |

Confirmed with the product owner: every organisation and project accepts the same
maximum file size (~16 MB, driven by WhatsApp media). What differs between projects
is how much space they get in total.

## Decision

**Per-file limits are global**, in YAML:

```yaml
media.validation:
  max-bytes: 16777216
  max-bytes-by-media-type: { IMAGE: 8388608, ... }
  allowed-mime-types-by-media-type: { ... }
```

**Total capacity stays per-tenant, in the database** — `project_storage.max_bytes`,
set at runtime through `/internal/quota/project`, with the invariant that a project
limit may never exceed its organisation's total.

The dividing line: **deployment configuration goes in YAML, tenant data goes in the
database.** A per-file limit is the same for everyone and changes with a release.
A capacity allowance is different for everyone and changes whenever a tenant is
onboarded or upgraded.

## Consequences

- Raising the file-size ceiling is a YAML edit and a restart — a deliberate,
  reviewed, fleet-wide change, which is the correct shape for a limit that
  protects the service itself.
- Onboarding a tenant needs **no deploy**. It is one API call to the existing
  provisioning endpoint.
- `MediaValidationService` takes no `TenantRef`: there is no per-tenant resolution
  to perform, so there is no code path that could resolve it wrongly.
- A per-type entry above `max-bytes` is **clamped down**, never up, so one careless
  override cannot raise the global ceiling.
- Startup fails on an empty allowlist — otherwise every upload returns 415 with no
  obvious cause.

## History — what the earlier draft got wrong

The first version of this ADR defined **named profiles assigned per org and per
project in YAML**:

```yaml
by-org:     { "42": extended }
by-project: { "42:7": lightweight }
```

That was wrong, for reasons that were visible at the time:

1. **Orgs and projects are created at runtime, continuously.** A YAML assignment
   map makes tenant onboarding a deploy-pipeline event. Workable at three tenants,
   broken at thirty, and it does not degrade gradually.
2. **It split tenant configuration across two stores.** A project's capacity lived
   in MySQL; its file-size limit would have lived in YAML. Same tenant, same
   concept, two sources of truth, two change processes — someone updates one and
   not the other.
3. **The escape hatch was written alongside the feature.** The original text said
   "past roughly a dozen entries, move this to a database column." Documenting the
   migration away from a design at the moment of adopting it is a sign the design
   was already known to be wrong.

The profile *shapes* were a reasonable idea; the runtime *assignment* in static
configuration was not. Since no tenant needs a different file-size limit, both are
removed rather than half-kept.

## Revisit when

A tenant genuinely needs a different per-file limit. The change is then a
`limit_profile VARCHAR(50) NULL` column on `project_storage`, set through the
provisioning API the organisation service already calls — **not** a YAML map.
