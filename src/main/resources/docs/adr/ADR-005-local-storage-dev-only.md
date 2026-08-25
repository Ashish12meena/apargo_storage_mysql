# ADR-005 — Local filesystem storage is development-only, enforced

**Status:** Accepted · 2026-08-20

## Context

Local filesystem storage is presented as a first-class provider: same interface as
S3, selected by a config toggle. Nothing prevents production use. Two replicas
without a shared volume means uploads to pod A return 404 from pod B.

## Decision

Local storage is **development-only**, and the constraint is **enforced**: startup
fails if the `prod` profile is active with `active-provider: local`.

Docker Compose uses **MinIO**, so the normal development path exercises the same S3
code as production. The local adapter exists only for a no-container run.

## Consequences

- A whole class of production incident becomes impossible rather than discouraged.
- Developers exercise the production code path by default, so S3-specific bugs
  surface locally.
- `presignPut` is unsupported on the local adapter and throws; callers select the
  proxied path when the provider cannot presign.
- Someone will eventually want local storage in a small production deployment. The
  answer is MinIO, which is S3-compatible and does not fork the code path.

## Alternatives rejected

**Support local storage in production with a shared volume (NFS/EFS).** Introduces
a distributed filesystem with its own failure modes, and the code would still need
a second correct path for locking, atomic writes, and replication.

**Document the constraint without enforcing it.** The current service does exactly
this, and the review found nothing preventing misuse. A comment is not a control.
