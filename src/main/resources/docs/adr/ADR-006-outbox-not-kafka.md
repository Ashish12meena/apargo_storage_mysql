# ADR-006 — Transactional outbox, not Kafka

**Status:** Accepted · 2026-08-20

## Context

Async effects are needed: physical deletion after soft delete, WhatsApp publishing
out of the hot path, future virus scanning. The requirement is that a state change
and its event are atomic.

## Decision

**A database outbox table with a poller.** No broker in Phases 1–3.

Events are inserted in the same transaction as the state change. A poller claims
batches with `FOR UPDATE SKIP LOCKED` and dispatches with exponential backoff,
DLQ-ing after 10 attempts.

## Consequences

- Atomicity is guaranteed by the database transaction, which is the actual
  requirement.
- No new infrastructure to operate, monitor, secure, or upgrade.
- `SKIP LOCKED` lets every replica poll concurrently with no coordination.
- Polling adds up to 1 second of latency. Acceptable for every current consumer.
- The outbox table needs its own retention policy — dispatched rows are deleted
  after 7 days, or it becomes the largest table in the schema within a year.
- Throughput is bounded by database write capacity, roughly thousands per second —
  well above the projected volume.

## Revisit when

- A second independent consumer exists.
- Volume exceeds ~1,000 events/s sustained.
- A consumer needs replay from an arbitrary offset.
- Fan-out to teams outside this one.

The schema is deliberately Kafka-shaped, so migration swaps the dispatcher for a
producer without changing event contracts.

## Alternatives rejected

**Kafka now.** Adds partitions, consumer groups, rebalancing, schema registry, and
DLQ topics for one best-effort consumer and one internal reaper. Critically, **it
does not solve the atomicity problem** — publishing after commit still has a loss
window, and before commit has a phantom window. An outbox would be needed anyway.

**Spring `@TransactionalEventListener`.** In-process only: events are lost on
crash, and there is no retry or dead-letter path.
