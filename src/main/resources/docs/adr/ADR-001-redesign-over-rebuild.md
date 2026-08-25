# ADR-001 — Major redesign, not refactor or rebuild

**Status:** Accepted · 2026-08-20

## Context

The existing service is Spring Boot 3.5 / Java 21, ~108 files. It has real strengths
(hexagonal layering, a correct quota engine, good exception mapping) alongside
disqualifying gaps (no verified authorization, no working delete, no content
verification, no container build, committed credentials).

Three options: refactor in place, redesign in place, or rebuild alongside.

## Decision

**Major redesign of the existing service.** Keep the skeleton, replace the
perimeter, rebuild the middle.

## Consequences

- The quota engine — the hardest part to get right, and already correct under
  concurrency — is preserved rather than re-derived.
- The package layout survives, so the team's existing mental model still applies.
- No parallel-service cutover, no dual-write window, no data migration beyond
  additive columns.
- Breaking changes are still required (authentication, pagination), handled through
  deprecation windows rather than a flag day.
- Roughly 35% kept, 40% modified, 20% replaced, 5% deleted.

## Alternatives rejected

**Refactor.** Understates the scope. Authorization, the upload lifecycle, delete,
rate limiting, and secret handling must be *replaced*, not improved. Calling it a
refactor would let the riskiest items be deprioritised as "cleanup".

**Rebuild.** Would discard proven quota concurrency code and re-litigate a package
layout that is already correct, in exchange for no architectural gain. The problems
are additive and replaceable in place, not structural. A rebuild also implies a
migration and cutover risk that the actual defects do not justify.
