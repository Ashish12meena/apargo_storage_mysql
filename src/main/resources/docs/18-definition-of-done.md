# 18 — Definition of Done

An item is done when **every** box is ticked. Partial completion is not completion,
and "done except for the docs" is not done.

## 1. Every work item

- [ ] The requirement it satisfies is identified by ID from `03-requirements.md`
- [ ] The relevant documentation was written **before** the code
- [ ] Contracts (`port.in`, `port.out`, DTOs) match the documentation exactly
- [ ] Implementation matches the contracts
- [ ] Documentation updated if the design changed during implementation
- [ ] `15-implementation-status.md` updated
- [ ] Any new assumption, risk, or open decision recorded in `17-risks-assumptions.md`
- [ ] No `TODO` or commented-out code left behind — git has the history
- [ ] ArchUnit boundary rules pass
- [ ] `gitleaks` passes

## 2. Every code change (from Phase 1)

- [ ] Unit tests for business rules; integration tests for anything crossing a boundary
- [ ] Concurrency test where concurrent access is possible
- [ ] Error paths tested, not just the happy path
- [ ] Metrics emitted for anything worth alerting on
- [ ] Structured logging at the right level; no secret, token, presigned URL, or storage key above DEBUG
- [ ] No secret with a default value
- [ ] No storage call inside a database transaction
- [ ] No synchronous third-party call in a request path
- [ ] Every repository method is tenant-scoped
- [ ] OpenAPI updated for any API change

## 3. Every API change

- [ ] Documented in `04-api-contracts.md` **first**
- [ ] Backward compatible, or a migration window is defined with dates
- [ ] Error codes added to `ErrorCode` (append-only)
- [ ] Status codes match the mapping in `10-error-handling.md`
- [ ] `Idempotency-Key` supported if mutating
- [ ] Consumers identified and notified
- [ ] `Deprecation` / `Sunset` headers on anything being retired

## 4. Every schema change

- [ ] Documented in `06-database-design.md` first
- [ ] A numbered Flyway migration — never an edit to an existing one
- [ ] Forward-only; no `DROP SCHEMA`, `DROP TABLE`, or `TRUNCATE`
- [ ] Backward compatible with the previous release
- [ ] Index impact considered — new indexes cost write throughput
- [ ] Tested against a production-sized dataset if the table is large
- [ ] Rollback plan that does **not** require a down-migration

## 5. Phase exit

- [ ] Every task in the phase is done by §1
- [ ] Every requirement mapped to the phase is satisfied and demonstrable
- [ ] Documentation is internally consistent — no contradictions between documents
- [ ] The service is deployable and revertible from this state
- [ ] Integration tests pass against real dependencies (Testcontainers)
- [ ] Security review of anything touching authn, authz, or secrets
- [ ] Dashboards and alerts exist for anything new
- [ ] Runbook entries for any new failure mode
- [ ] `15-implementation-status.md` reflects reality
- [ ] Open decisions needed by the next phase are **resolved**, not deferred again

## 6. Production readiness (Phase 2 exit specifically)

The first phase whose exit means "deployable to production". Additionally:

- [ ] No credential in source or git history; `gitleaks` clean on the full history
- [ ] `/internal/**` authenticated **and** network-restricted
- [ ] Every tenant-facing endpoint requires a verified credential
- [ ] Cross-tenant access verified impossible by test, including with a known storage key
- [ ] Delete removes the stored object and releases quota — **proven end to end**
- [ ] Quota exact under a 100-thread concurrency test against real MySQL
- [ ] Idempotent retries produce one file and one quota charge
- [ ] Content-type mismatch rejected, verified with a polyglot file
- [ ] Container image builds reproducibly in CI, runs non-root, passes Trivy
- [ ] Liveness and readiness are distinct and correct
- [ ] Zero-downtime deploy demonstrated
- [ ] Load test meets NFR-01 through NFR-05
- [ ] Restore from backup tested

## 7. What "done" is not

- Code that works but is undocumented
- Documentation written after the fact to match what was built
- A feature behind a flag that nobody has enabled
- Tests that pass because they assert current behaviour rather than required behaviour
- An item marked complete in the status document while its blocker is unresolved
- "We'll add metrics later" — later does not arrive under load
