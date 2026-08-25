# ADR-008 — Scheduler locks live in the database

**Status:** Accepted · 2026-08-20

## Context

Every `@Scheduled` method in the current service runs on every replica. Invisible at
one instance; at two, quota reconciliation and file cleanup run concurrently over
the same rows. Redis is already being introduced for rate limiting and could host
locks.

## Decision

**Lease-based locks in a `scheduler_lock` table.** Not Redis.

## Consequences

- The lock lives in the same store as the data it protects, so it cannot be lost by
  an independent failure — a Redis eviction or restart cannot cause two
  reconciliations to run against MySQL.
- Lease expiry means an ungracefully terminated pod does not hold a lock forever.
- One extra database round trip per scheduled run. Irrelevant at cron frequency.
- Redis stays a pure cache, which keeps its failure mode simple: fail open, never a
  source of truth for anything.

## Alternatives rejected

**Redis locks (Redlock or `SET NX`).** Faster, and correctness under failover is
contested. Reconciliation runs once a night — latency is not a consideration, and
correctness is.

**Leader election.** More machinery than the problem warrants for a handful of cron
jobs.
