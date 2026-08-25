# 06 — Database Design and Scalability

**MySQL 8.0, InnoDB, `utf8mb4`.** The source of truth for everything this service
owns. Storage may lag the database; it may never lead it.

## 1. Migration strategy

### Current state is broken

`spring.flyway.locations` points at `classpath:db/migration`, **which does not
exist**, while `ddl-auto: validate` requires a schema that only `db/storage.sql`
provides — a script whose first statement is `drop schema if exists
apargo_storage_mysql`. A fresh environment cannot start without a manual step, and
that manual step is destructive. The same file ends with unconditional `INSERT`s
seeding fictional quota rows for orgs 1 and 2, which would run against a production
first deploy.

### Layout

```
db/migration/                       ← Flyway path. Schema only.
  V1__baseline_schema.sql           reproduces today's live schema exactly
  V2__media_lifecycle.sql
  V3__upload_session.sql
  V4__idempotency_record.sql
  V5__outbox_event.sql
  V6__media_audit.sql
  V7__scheduler_lock.sql
  V8__media_purge_after.sql
db/seed/                            ← NEVER on the Flyway path
  local_quota_seed.sql              applied only by the local profile
```

### Rules

1. `V1` reproduces the live schema exactly, so `baseline-on-migrate` is truthful.
2. **No `DROP SCHEMA`, `DROP TABLE`, or `TRUNCATE` in any migration.** Ever.
3. Forward-only. No down-migrations — a rollback that runs backwards through a
   schema change is more dangerous than the bug it undoes.
4. Every migration is backward-compatible with the previous release, so a code
   rollback does not require a schema rollback.
5. Column changes use expand/contract across separate releases: add nullable →
   backfill → dual-write → switch reads → drop old.
6. Seeds are never in the migration path.
7. `ddl-auto: validate` in every environment. **Never `update`** — it is
   additive-only and best-effort, so it silently drifts between environments.
   Flyway creates every table at startup; nothing is created by hand.
8. Every table has a JPA entity and all access goes through Spring Data. See
   `02-package-structure.md §3` for the `@Modifying` rules that come with that.

## 2. `media`

### Kept from the current schema

The composite indexes are well chosen — they match the actual read paths rather
than being generic. `storage_key VARCHAR(1000)` and `media_url VARCHAR(2048)` are
generous and fine.

### Changes

| Column | Change | Reason |
|---|---|---|
| `status` | Extend to `PENDING`/`ACTIVE`/`EXPIRED`/`DELETED`/`PURGED`/`QUARANTINED` | Lifecycle. Today only `ACTIVE`/`DELETED` exist and nothing writes `DELETED`. |
| `deleted_by` | **ADD** `BIGINT NULL` | `softDeleteById` accepts this argument today and silently discards it — no column exists. |
| `created_by` | **ADD** `BIGINT NULL` | No per-user attribution exists anywhere. |
| `checksum_sha256` | **ADD** `CHAR(64) NULL` | Integrity; commented out in the current entity. |
| `detected_mime_type` | **ADD** `VARCHAR(100) NULL` | The sniffed type, distinct from the declared one. |
| `scan_status` | **ADD** `VARCHAR(20) NOT NULL DEFAULT 'SKIPPED'` | Ships now, used in Phase 4 — no later migration. |
| `upload_session_id` | **ADD** `CHAR(36) NULL` | Links the row to its reservation. |
| `version` | **ADD** `BIGINT NOT NULL DEFAULT 0` | Optimistic locking on metadata edits. |
| `deleted_at` | Now has a writer **and** a reader | Today it is a column with neither. |
| `purge_after` | **ADD** `DATETIME(6) NULL` (V8) | Separates WHEN a row was deleted from WHEN it may be purged. `deleted_at` is audit fact; `purge_after` is policy. This is what makes `?permanent=true` actually immediate. |
| `media_url` | **DEPRECATE**, drop in Phase 4 | A persisted presigned URL is stale when written. Computed on read instead. |
| `media_id`, `waba_id` | **DEPRECATE** | WhatsApp concerns in a storage schema. Removed if ADR-009 is accepted. |

### Indexes

| Index | Purpose |
|---|---|
| `(organisation_id, project_id, created_at DESC, id DESC)` | **Keyset pagination.** Extends the existing index with `id` as a tiebreaker so the cursor is total-ordered. |
| `(organisation_id, project_id, media_type, created_at DESC, id DESC)` | Filtered listing |
| `UNIQUE (storage_key)` | **NEW.** Makes key reuse structurally impossible rather than relying on UUID collision odds. |
| `(status, purge_after)` | Reaper query — replaces `(status, deleted_at)` |
| `(status, created_at)` | Orphan and expiry sweeps |
| `(upload_session_id)` | Commit lookup |

Dropped: `idx_media_stored_filename` and `idx_media_media_id` — neither has a query
in this codebase, and every unused index is write amplification on the hottest
insert path.

## 3. Quota tables — unchanged

`org_storage` and `project_storage` are left alone. The composite primary key, the
`version` column, the foreign key from project to org, and the non-negative `CHECK`
constraints are all correct. Composite-key optimistic locking through JPA is not
trivial to get right and it is right here.

The only change is *how* they are written — one conditional statement rather than
read-modify-write (§5).

## 4. New tables

### 4.1 `upload_session`

```sql
CREATE TABLE upload_session (
    id                CHAR(36)     NOT NULL PRIMARY KEY,
    org_id            BIGINT       NOT NULL,
    project_id        BIGINT       NOT NULL,
    media_id          BIGINT       NULL,
    storage_key       VARCHAR(1000) NOT NULL,
    mode              VARCHAR(30)  NOT NULL,
    declared_size     BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    idempotency_key   VARCHAR(255) NULL,
    provider_upload_id VARCHAR(255) NULL,
    created_at        DATETIME(6)  NOT NULL,
    expires_at        DATETIME(6)  NOT NULL,
    INDEX idx_session_sweep (status, expires_at),
    INDEX idx_session_tenant (org_id, project_id)
) ENGINE=InnoDB;
```

`idx_session_sweep` is the sweeper's only query. Without it the sweep degrades into
a full scan as sessions accumulate.

### 4.2 `idempotency_record`

```sql
CREATE TABLE idempotency_record (
    org_id          BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    response_status INT          NULL,
    response_body   JSON         NULL,
    created_at      DATETIME(6)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (org_id, project_id, idempotency_key),
    INDEX idx_idem_expiry (expires_at)
) ENGINE=InnoDB;
```

Tenant-scoped primary key: keys cannot collide or be probed across orgs. Concurrent
duplicates are detected by the **unique-key violation on insert**, not by a
read-then-write, which would have its own race.

### 4.3 `outbox_event`

```sql
CREATE TABLE outbox_event (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    dispatched_at  DATETIME(6)  NULL,
    last_error     VARCHAR(500) NULL,
    INDEX idx_outbox_dispatch (status, next_retry_at, id)
) ENGINE=InnoDB;
```

Dispatched rows are deleted after 7 days. An outbox that only grows becomes the
largest table in the schema within a year.

### 4.4 `media_audit`

```sql
CREATE TABLE media_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    org_id       BIGINT       NOT NULL,
    project_id   BIGINT       NOT NULL,
    actor_id     VARCHAR(64)  NULL,
    actor_type   VARCHAR(20)  NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    resource_id  VARCHAR(64)  NULL,
    detail       JSON         NULL,
    client_ip    VARCHAR(45)  NULL,
    trace_id     VARCHAR(64)  NULL,
    occurred_at  DATETIME(6)  NOT NULL,
    INDEX idx_audit_tenant_time (org_id, project_id, occurred_at DESC),
    INDEX idx_audit_resource (resource_id)
) ENGINE=InnoDB;
```

Append-only. **The application's database role has no `UPDATE` or `DELETE` grant on
this table** — a convention that can be violated is not an audit trail.

### 4.5 `scheduler_lock`

```sql
CREATE TABLE scheduler_lock (
    lock_name   VARCHAR(100) NOT NULL PRIMARY KEY,
    locked_by   VARCHAR(100) NOT NULL,
    locked_at   DATETIME(6)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL
) ENGINE=InnoDB;
```

Lease-based with an expiry, so an ungracefully terminated pod does not hold a lock
forever. Every `@Scheduled` method in the current service runs on every replica.

## 5. Quota reservation — the critical statement

```sql
-- 1. project first, always
UPDATE project_storage
   SET used_bytes = used_bytes + :size, updated_at = NOW(6)
 WHERE org_id = :orgId AND project_id = :projectId
   AND used_bytes + :size <= max_bytes;
-- rows = 0 → exceeded, or not provisioned. Disambiguate with a follow-up SELECT.

-- 2. org second, always
UPDATE org_storage
   SET used_bytes = used_bytes + :size, updated_at = NOW(6)
 WHERE org_id = :orgId
   AND used_bytes + :size <= max_bytes;
-- rows = 0 → roll back the transaction. Do NOT hand-compensate.
```

Both statements run in one transaction. The current implementation issues an
explicit compensating decrement before throwing, inside a `REQUIRES_NEW`
transaction that is about to roll back anyway — harmless today, and a
double-decrement bug the moment somebody changes the propagation.

Release floors at zero:

```sql
UPDATE project_storage
   SET used_bytes = CASE WHEN used_bytes >= :size THEN used_bytes - :size ELSE 0 END
 WHERE org_id = :orgId AND project_id = :projectId;
```

Why not a row lock or optimistic retry: a lock serialises every upload in a
project; a retry loop degrades under exactly the contention it exists to handle,
and 5 attempts is an arbitrary ceiling. A conditional `UPDATE` has neither problem
and enforces the invariant in the engine. See [ADR-003](adr/ADR-003-quota-concurrency.md).

## 6. Transaction boundaries

| Operation | Transaction |
|---|---|
| Initiate upload | quota reserve + `media` PENDING + `upload_session` + audit |
| Complete upload | `media` → ACTIVE + session → COMMITTED + outbox + audit |
| Delete | `media` → DELETED + quota release + outbox + audit |
| Purge | `media` → PURGED (after the object is confirmed gone) |
| Reserve → object write | **Separate.** The storage call is outside any transaction. |

**No storage call, no HTTP call, and no message publish occurs inside a database
transaction.** A network call inside a transaction holds a connection for the
duration of a remote operation and can exhaust the pool under load.

## 7. Connection pool

| Setting | Value | Reasoning |
|---|---|---|
| `maximum-pool-size` | 30 | Well below MySQL `max_connections` across 20 replicas |
| `minimum-idle` | 10 | |
| `connection-timeout` | 3 s | Fail fast; do not queue behind an exhausted pool |
| `max-lifetime` | 25 min | Under any proxy or MySQL idle timeout |
| `leak-detection-threshold` | 30 s | |

Tomcat's 200 threads deliberately exceed the 30-connection pool: requests queue on
the pool rather than on the database, and most requests do not hold a connection
for their whole duration.

## 8. Scale plan

| Row count | Action |
|---|---|
| < 10 M | Nothing. Current indexes suffice. |
| 10–50 M | Verify keyset plans; add covering indexes if listing regresses. |
| 50 M+ | Partition `media` by `organisation_id` range. The key is chosen now because every existing index already leads with it; execution is deferred until the pain is real. |
| 200 M+ | Consider a read replica for listing, or archive `PURGED` rows older than a year to cold storage. |

Two things are done **now** because retrofitting them is expensive: keyset
pagination (offset degrades linearly and rewriting the API later is breaking) and
choosing the partition key.

Deliberately deferred: sharding, a separate read model, and a time-series store for
audit. None is justified at current scale, and each adds operational surface that
must be maintained forever.
