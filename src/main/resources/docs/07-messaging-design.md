# 07 — Messaging Design

## 1. Decision: transactional outbox, no broker

**No Kafka in Phases 1–3.** A database outbox table with a poller.

### Why

The problem to solve is that a database commit and a message publish must be
atomic. A broker **does not solve this** — publishing to Kafka after a commit still
has a window where the commit succeeds and the publish does not, and publishing
before the commit has the inverse. The outbox solves it, because the event is
inserted in the same transaction as the state change.

Kafka would add partitions, consumer groups, rebalancing, schema registry, DLQ
topics, and a cluster to operate — for **one** downstream effect today (a
best-effort WhatsApp push) and one internal consumer (the delete reaper). That is
operational surface disproportionate to the problem.

### When to revisit

Adopt a broker when **any** of these becomes true:

- A second independent consumer exists (search indexing, analytics, another service
  reacting to `media.created`).
- Event volume exceeds ~1,000/s sustained.
- A consumer needs replay from an arbitrary offset.
- Fan-out to consumers outside this team's control.

The outbox schema is deliberately Kafka-shaped (`aggregate_type`, `aggregate_id`,
`event_type`, JSON payload), so migration swaps the dispatcher for a producer. The
event contracts do not change. See [ADR-006](adr/ADR-006-outbox-not-kafka.md).

## 2. Mechanism

```
   ┌── one transaction ──────────────────────┐
   │  UPDATE media SET status='DELETED' ...  │
   │  UPDATE project_storage ...             │
   │  INSERT INTO outbox_event ...           │
   └──────────────── COMMIT ─────────────────┘
                     │
              poller (every 1s, all replicas)
                     │  SELECT ... WHERE status='PENDING'
                     │    AND next_retry_at <= NOW()
                     │  ORDER BY id LIMIT 100
                     │  FOR UPDATE SKIP LOCKED
                     ▼
              ┌─────────────┐
              │  handlers   │  idempotent, at-least-once
              └──────┬──────┘
             success │ failure
                     ▼
        DISPATCHED   │   attempts++, backoff, → FAILED after 10 → alert
```

`FOR UPDATE SKIP LOCKED` lets every replica poll concurrently without coordination:
each claims a disjoint batch. No leader election, no partition assignment.

## 3. Delivery semantics

**At-least-once.** Every handler must be idempotent. Exactly-once does not exist
across a database and an external system; pretending otherwise produces subtle
duplicate-effect bugs.

Consequences handlers must accept:
- Deleting an already-absent object is a **success**.
- Publishing an already-published media item is a no-op.
- Emitting a duplicate metric is acceptable.

**Ordering** is per-aggregate only, via `ORDER BY id` within a batch. There is no
global ordering guarantee and none is needed.

**Retry:** exponential backoff, base 2 s, factor 2, jitter, cap 5 min, 10 attempts
(~30 min total). Then `FAILED` and an alert — a real dead-letter path, which the
current fire-and-forget WhatsApp push does not have. A failed push today is logged,
swallowed, and afterwards undiscoverable.

## 4. Events

| Event | Emitted when | Consumers |
|---|---|---|
| `media.created` | Media becomes ACTIVE | WhatsApp publisher (transitional), virus scanner (P4) |
| `media.deleted` | ACTIVE → DELETED | **Media reaper** (removes the object) |
| `media.purged` | Object confirmed gone | Audit |
| `media.quarantined` | Scan returns INFECTED | Notification (P4) |
| `quota.threshold.crossed` | Utilisation crosses 80% / 95% | Notification (P3) |
| `upload.session.expired` | Sweeper reclaims a session | Metrics |
| `tenant.teardown.requested` | Offboarding requested | **Teardown handler** (self-requeuing, batched) |
| `tenant.teardown.completed` | No live rows remain for the tenant | Notification, audit |

### Payload rules

Payloads carry **identifiers only**. Never file bytes, never presigned URLs, never
credentials. A URL in a payload is a capability sitting in a table that outlives
its own expiry semantics.

```jsonc
{
  "eventId": "uuid",
  "eventType": "media.deleted",
  "eventVersion": 1,
  "occurredAt": "2026-08-20T10:15:00Z",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "orgId": 42, "projectId": 7,
  "data": { "mediaId": "1234", "storageKey": "org-42/proj-7/image/uuid.png",
            "sizeBytes": 204800, "permanent": false }
}
```

`eventVersion` is present from day one. Adding it later means every consumer must
handle both a versioned and an unversioned form.

### Schema evolution

Additive only within a version: new optional fields are fine; renaming, removing,
or retyping a field requires `eventVersion` 2, with both emitted during a
transition window.

## 4b. Teardown as a self-requeuing event

An organisation teardown may span millions of rows. Rather than a second job
system, the handler marks one bounded batch (500 rows) and **re-appends its own
event** with an incremented counter if any remain.

This reuses the outbox's retry, backoff, and dead-letter machinery, and makes
crash recovery free: the continuation predicate is "rows still live", not a stored
offset, so resuming needs no bookkeeping.

The event carries a **running actual total** of rows marked, not a count derived
from `batchesDone × batchSize`. The derived figure overcounts, because the final
batch is nearly always partial — a 501-file teardown with a 500-row batch would
have reported 1000. A `MAX_BATCHES` guard turns a
non-transitioning row from an infinite loop into a dead-lettered event.

Storage is not touched by the handler. Rows become `DELETED` with a `purge_after`,
and the existing purge scan removes the objects — one reaper, one set of failure
modes.

## 5. The delete reaper — the critical consumer

Handles `media.deleted`:

1. Read the media row; if already `PURGED`, acknowledge and stop.
2. If soft-deleted and inside the grace period, requeue with a delay.
3. `storagePort.delete(key)` — absent key counts as success.
4. `media` → `PURGED`, emit `media.purged`.

**Order matters.** Marking `PURGED` before the delete succeeds loses the record of
what still needs removing. Deleting inside the API transaction risks a rollback
after the object is gone.

## 6. What is deliberately not evented

| Not evented | Why |
|---|---|
| Quota reservation | Synchronous and transactional. An eventual-consistency quota model would let tenants overshoot their limit by an unbounded amount during lag. |
| Upload completion | The client needs the result synchronously. |
| Metadata reads | No state change. |

An asynchronous quota model was considered and rejected: the entire value of quota
is the bound, and a bound that lags is not a bound.
