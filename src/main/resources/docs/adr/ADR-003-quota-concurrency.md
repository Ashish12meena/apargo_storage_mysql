# ADR-003 — Atomic conditional UPDATE for quota

**Status:** Accepted · 2026-08-20

## Context

The current service ships **three** quota mechanisms at once:

1. Pessimistic row locks with `Propagation.MANDATORY`
2. Optimistic `@Version` + `@Retryable(maxAttempts = 5)`
3. Atomic `UPDATE ... WHERE used_bytes + ? <= max_bytes`

All three are correct. Only one should survive.

## Decision

**Keep (3).** Delete (1) and (2).

```sql
UPDATE project_storage
   SET used_bytes = used_bytes + :size
 WHERE org_id = :o AND project_id = :p
   AND used_bytes + :size <= max_bytes;
```

Preserve the documented **project-then-org** lock ordering. Remove the redundant
hand-written compensating decrement in `reserveQuotaAtomic` — the enclosing
transaction rolls back on throw, so the compensation is dead code that becomes a
double-decrement bug the moment propagation changes.

## Consequences

- No row lock: concurrent uploads to one project are not serialised.
- No retry loop: nothing to tune, nothing that degrades under contention.
- The invariant is enforced by the database engine, so it holds even if application
  code is wrong.
- Rows affected (1 or 0) is the result; a follow-up `SELECT` distinguishes
  "exceeded" from "not provisioned", which map to different statuses.
- Quota reservation goes through JDBC rather than the persistence context, so it
  cannot be accidentally turned into a read-modify-write.

## Alternatives rejected

**Pessimistic locking.** Serialises every upload in a project behind one row lock.
Also requires an enclosing transaction, which forces the storage write inside it.

**Optimistic + retry.** Degrades under exactly the contention it exists to handle,
and 5 attempts is an arbitrary ceiling — at high concurrency some callers exhaust
retries and fail spuriously despite available quota.

**Redis counters with periodic DB flush.** Adds a consistency problem to a solved
problem, and makes the bound approximate. Reconsider only if reservation p95 exceeds
50 ms.
