# Storage Service — Documentation

**These documents are the source of truth.** Where code and documentation disagree,
the documentation is correct and the code is a defect. No implementation starts
until the relevant document is written, reviewed, and internally consistent with
the contracts in `application/port/**` and `api/dto/**`.

## Working agreement

Every unit of work follows this order, without exception:

```
Requirement → Documentation → Design/Contract → Implementation → Doc Update → Status Update
```

Skipping the first two produces the situation this service is being rebuilt from:
two upload implementations with divergent semantics, a delete path nothing calls,
and a security model asserted in comments rather than enforced in code.

## Documents

| # | Document | Answers |
|---|---|---|
| 01 | [architecture.md](01-architecture.md) | What this service is responsible for, and what it is not |
| 02 | [package-structure.md](02-package-structure.md) | Where code goes and which dependencies are legal |
| 03 | [requirements.md](03-requirements.md) | Functional and non-functional requirements, with targets |
| 04 | [api-contracts.md](04-api-contracts.md) | Every endpoint, and what may never change |
| 05 | [domain-design.md](05-domain-design.md) | Aggregates, invariants, state machines, business rules |
| 06 | [database-design.md](06-database-design.md) | Schema, indexes, migrations, scale plan |
| 07 | [messaging-design.md](07-messaging-design.md) | Outbox, events, why not Kafka yet |
| 08 | [integrations.md](08-integrations.md) | Who calls us, who we call, and on what terms |
| 09 | [security.md](09-security.md) | Authn, authz, tenant isolation, secrets |
| 10 | [error-handling.md](10-error-handling.md) | Exception taxonomy and HTTP mapping |
| 11 | [production-readiness.md](11-production-readiness.md) | Observability, health, deployment gates |
| 12 | [scalability-performance.md](12-scalability-performance.md) | Bottlenecks, targets, scaling levers |
| 13 | [configuration.md](13-configuration.md) | Every property, every secret, every default |
| 14 | [implementation-phases.md](14-implementation-phases.md) | Phased plan with exit criteria |
| 15 | [implementation-status.md](15-implementation-status.md) | **Live.** Done / doing / next / blocked |
| 16 | [adr/](adr/) | Architecture decisions and their rationale |
| 17 | [risks-assumptions.md](17-risks-assumptions.md) | What could bite us, what we are assuming |
| 18 | [definition-of-done.md](18-definition-of-done.md) | When a phase is actually finished |

## Baseline

This service is a **major redesign** of an existing Spring Boot 3.5 / Java 21
service, not a greenfield build and not a rebuild. The reasoning is in
[ADR-001](adr/ADR-001-redesign-over-rebuild.md); the audit of what exists today is
in [01-architecture.md §7](01-architecture.md#7-baseline-audit).

**Read [17-risks-assumptions.md](17-risks-assumptions.md) first if you are new.**
It records what we are assuming about the surrounding services, and several of those
assumptions are unverified.
