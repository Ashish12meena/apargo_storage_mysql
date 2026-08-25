# Architecture Decision Records

One decision per file. Immutable once accepted — a reversal is a **new** ADR that
supersedes the old one, so the reasoning trail survives.

Format: Context → Decision → Consequences → Alternatives rejected.

| ADR | Decision | Status |
|---|---|---|
| [001](ADR-001-redesign-over-rebuild.md) | Major redesign, not refactor or rebuild | Accepted |
| [002](ADR-002-single-upload-path.md) | One upload implementation, not two | Accepted |
| [003](ADR-003-quota-concurrency.md) | Atomic conditional UPDATE for quota | Accepted |
| [004](ADR-004-control-plane.md) | Control plane — bytes bypass the service | Accepted |
| [005](ADR-005-local-storage-dev-only.md) | Local filesystem is dev-only, enforced | Accepted |
| [006](ADR-006-outbox-not-kafka.md) | Transactional outbox, no broker yet | Accepted |
| [007](ADR-007-ratelimit-fail-open.md) | Rate limiting fails open | Accepted |
| [008](ADR-008-db-locks-not-redis.md) | Scheduler locks in the database | Accepted |
| [009](ADR-009-whatsapp-boundary.md) | WhatsApp publishing does not belong here | **Proposed** |
| [010](ADR-010-jwt-verification.md) | Verify JWTs in-service | Accepted |
| [011](ADR-011-no-dedup.md) | No content deduplication | Accepted |
| [012](ADR-012-limit-profiles.md) | Per-file limits global; only capacity per-tenant | Accepted |
| [013](ADR-013-no-structural-validation.md) | No structural file validation | Accepted |
| [014](ADR-014-scanning-optional.md) | Malware scanning wired but disabled by default | Accepted |
