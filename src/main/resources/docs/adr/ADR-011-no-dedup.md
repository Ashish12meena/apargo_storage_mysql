# ADR-011 — No content deduplication

**Status:** Accepted · 2026-08-20

## Context

Checksums are being reintroduced (the current entity has the field commented out,
and the repository has a commented-out duplicate-detection query). Storing one copy
of identical content would save space.

## Decision

**No deduplication.** Checksums are for integrity verification and reconciliation
only.

## Consequences

- Identical files stored by different tenants occupy separate objects. Storage cost
  is higher; S3 storage is cheap and the amounts involved are small.
- Quota accounting stays simple: each media row owns its bytes, and deleting one
  never affects another.
- Deletion stays simple: no reference counting, no "is anyone else using this
  object" check on every delete.

## Alternatives rejected

**Cross-tenant dedup.** A **security problem**, not just a complexity one: a tenant
can determine whether another tenant holds a specific file by observing upload
timing or quota behaviour. That is a confirmed-file-existence oracle across the
isolation boundary.

**Same-tenant dedup.** No security issue, but it requires reference counting, and
quota release becomes conditional on the last reference. That turns delete — an
operation that must be simple and idempotent — into a distributed reference-counting
problem. Not worth it for the savings.

**Revisit if** storage cost becomes material *and* same-tenant duplication is
measured to be significant. Measure before building.
