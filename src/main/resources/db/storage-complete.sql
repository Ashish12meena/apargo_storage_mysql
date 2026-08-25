-- =============================================================================
--  Apargo Storage Service — complete schema
--  MySQL 8.0+ / InnoDB / utf8mb4
--
--  This is the FINAL state after migrations V1–V7, flattened into one file and
--  ordered by dependency. It exists for reference, local bootstrapping, and
--  review.
--
--  ⚠️  FLYWAY REMAINS THE SOURCE OF TRUTH.
--      Production schema is applied by src/main/resources/db/migration.
--      Do NOT run this file against an environment Flyway manages: the history
--      table would disagree with reality and the next migration would fail
--      validation. To adopt an existing database created from this file, run
--      Flyway with baseline-on-migrate=true and baseline-version=7.
--
--  Deliberately contains NO seed data and NO DROP statements. The predecessor's
--  script opened with `drop schema if exists` and ended with unconditional
--  INSERTs of fictional quota rows — both of which would run against a
--  production first deploy.
--
--  Object order below is FK-safe: org_storage precedes project_storage.
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- CREATE DATABASE IF NOT EXISTS apargo_storage
--     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE apargo_storage;


-- =============================================================================
--  1. QUOTA
--
--  Two scopes. Reservation locks PROJECT first, then ORG — always, never
--  reversed. Consistent ordering is what prevents deadlock between concurrent
--  uploads to different projects of one organisation.
--
--  The invariant used <= max is enforced by a conditional UPDATE at runtime,
--  not by application code:
--
--      UPDATE project_storage SET used_bytes = used_bytes + :n
--       WHERE org_id = ? AND project_id = ? AND used_bytes + :n <= max_bytes;
--
--  A further rule is enforced at provisioning time: a project limit may never
--  exceed its organisation's total.
-- =============================================================================

CREATE TABLE IF NOT EXISTS org_storage (
    org_id      BIGINT       NOT NULL,
    max_bytes   BIGINT       NOT NULL DEFAULT 0,
    used_bytes  BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (org_id),
    CONSTRAINT chk_org_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_org_max_non_negative  CHECK (max_bytes  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Organisation storage allowance and consumption';


CREATE TABLE IF NOT EXISTS project_storage (
    org_id      BIGINT       NOT NULL,
    project_id  BIGINT       NOT NULL,
    max_bytes   BIGINT       NOT NULL DEFAULT 0,
    used_bytes  BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (org_id, project_id),
    CONSTRAINT fk_project_storage_org
        FOREIGN KEY (org_id) REFERENCES org_storage (org_id),
    CONSTRAINT chk_proj_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_proj_max_non_negative  CHECK (max_bytes  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Project storage allowance; max_bytes must never exceed the org total';


-- =============================================================================
--  2. MEDIA  —  the primary aggregate
--
--  Lifecycle:
--    PENDING -> ACTIVE -> DELETED -> PURGED
--       |                    |
--       +-> EXPIRED          +-> ACTIVE (restore, within grace)
--    ACTIVE -> QUARANTINED (scan returned INFECTED)
--
--  PENDING is written BEFORE any byte reaches storage. That ordering is why a
--  stored object can never exist without a database row: a crash leaves a
--  sweepable row rather than an invisible, permanently-billed orphan.
--
--  organisation_id / project_id are foreign-key-SHAPED with no foreign key,
--  because the authoritative tenant registry lives in another service. That is
--  correct for a microservice; a periodic job flags rows whose tenant no longer
--  exists.
-- =============================================================================

CREATE TABLE IF NOT EXISTS media (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    organisation_id    BIGINT        NOT NULL,
    project_id         BIGINT        NOT NULL,
    created_by         BIGINT        NULL
                                     COMMENT 'Per-user attribution; absent in the predecessor',
    original_filename  VARCHAR(255)  NOT NULL,
    stored_filename    VARCHAR(255)  NULL
                                     COMMENT 'Generated object name; never returned to clients',
    storage_key        VARCHAR(512)  NOT NULL
                                     COMMENT 'org-{id}/proj-{id}/{type}/{uuid}.{ext} — capability-bearing, never logged above DEBUG',
    media_url          VARCHAR(2048) NULL
                                     COMMENT 'DEPRECATED: a persisted presigned URL is stale when written. Dropped in Phase 4.',
    mime_type          VARCHAR(100)  NOT NULL
                                     COMMENT 'Type the client DECLARED — diagnostics only',
    detected_mime_type VARCHAR(100)  NULL
                                     COMMENT 'Type detected from magic bytes — this is what is served',
    media_type         VARCHAR(20)   NOT NULL
                                     COMMENT 'IMAGE | VIDEO | AUDIO | DOCUMENT',
    file_size          BIGINT        NOT NULL,
    checksum_sha256    CHAR(64)      NULL
                                     COMMENT 'Integrity and reconciliation. NOT used for dedup (ADR-011)',
    storage_provider   VARCHAR(20)   NOT NULL
                                     COMMENT 'LOCAL | S3 | MINIO',
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                                     COMMENT 'PENDING | ACTIVE | EXPIRED | DELETED | PURGED | QUARANTINED',
    scan_status        VARCHAR(20)   NOT NULL DEFAULT 'SKIPPED'
                                     COMMENT 'SKIPPED | PENDING | CLEAN | INFECTED | FAILED',
    upload_session_id  CHAR(36)      NULL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at         DATETIME(6)   NULL
                                     COMMENT 'WHEN the delete happened — audit fact, never moves',
    deleted_by         BIGINT        NULL,
    purge_after        DATETIME(6)   NULL
                                     COMMENT 'WHEN removal is permitted — policy. now+grace normally, now for compliance erasure',
    version            BIGINT        NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    -- Makes storage-key reuse structurally impossible rather than relying on
    -- UUID collision odds. Prefix-limited because the column is 1000 chars.
    UNIQUE KEY uq_media_storage_key (storage_key),

    -- Keyset pagination. created_at alone is not unique, so id is the tiebreaker
    -- that gives the total order the cursor depends on.
    KEY idx_media_keyset       (organisation_id, project_id, created_at DESC, id DESC),
    KEY idx_media_keyset_type  (organisation_id, project_id, media_type, created_at DESC, id DESC),

    KEY idx_media_purge_after       (status, purge_after), -- purge scan
    KEY idx_media_status_created_at (status, created_at),  -- expiry sweep
    KEY idx_media_upload_session    (upload_session_id)    -- commit lookup
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Stored file metadata. The database is the source of truth; storage may lag it, never lead it.';


-- =============================================================================
--  3. UPLOAD SESSION  —  two-phase quota reservation
--
--  Quota is charged at RESERVED, not at commit. That is what makes concurrent
--  uploads correct: if quota were charged on commit, N concurrent uploads could
--  all pass a capacity check and collectively overcommit.
--
--  Any session that never reaches COMMITTED is reclaimed by one sweeper, so
--  recovery never depends on a catch block running — a catch block does not run
--  when the process dies, which is exactly when recovery is needed.
-- =============================================================================

CREATE TABLE IF NOT EXISTS upload_session (
    id                 CHAR(36)      NOT NULL,
    org_id             BIGINT        NOT NULL,
    project_id         BIGINT        NOT NULL,
    media_id           BIGINT        NULL,
    storage_key        VARCHAR(512)  NOT NULL,
    mode               VARCHAR(30)   NOT NULL
                                     COMMENT 'PROXIED | PRESIGNED_SINGLE | PRESIGNED_MULTIPART',
    declared_size      BIGINT        NOT NULL,
    status             VARCHAR(20)   NOT NULL
                                     COMMENT 'RESERVED | COMMITTED | ABORTED | EXPIRED',
    idempotency_key    VARCHAR(255)  NULL,
    provider_upload_id VARCHAR(255)  NULL
                                     COMMENT 'S3 multipart uploadId',
    created_at         DATETIME(6)   NOT NULL,
    expires_at         DATETIME(6)   NOT NULL,
    completed_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    -- The sweeper's only query. Without this it degrades to a full scan as
    -- sessions accumulate.
    KEY idx_session_sweep  (status, expires_at),
    KEY idx_session_tenant (org_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quota reservation with a TTL, bound to one prospective object';


-- =============================================================================
--  4. IDEMPOTENCY
--
--  template-service retries. Without this, a retry after a timeout creates a
--  second file, a second row, and a second quota charge.
--
--  The PK is tenant-scoped so keys cannot collide or be probed across orgs.
--  request_hash is stored so reusing a key for a DIFFERENT payload is rejected
--  (422) rather than silently replaying the wrong response.
--
--  Concurrent duplicates are detected by the UNIQUE violation on insert — not
--  by a read-then-write, which would have its own race.
-- =============================================================================

CREATE TABLE IF NOT EXISTS idempotency_record (
    org_id          BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL
                                 COMMENT 'IN_PROGRESS | COMPLETED | FAILED',
    response_status INT          NULL,
    response_body   JSON         NULL
                                 COMMENT 'Stores the media id, not a serialised body, so a replay cannot drift from the row',
    created_at      DATETIME(6)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (org_id, project_id, idempotency_key),
    KEY idx_idem_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Retry safety for mutating endpoints; rows expire after 24h';


-- =============================================================================
--  5. OUTBOX
--
--  Events are inserted in the SAME transaction as the state change that
--  produced them. That atomicity is why a table is used instead of publishing
--  straight to a broker: a direct publish cannot be made atomic with a commit
--  without two-phase commit or a loss window (ADR-006).
--
--  The dispatcher claims batches with FOR UPDATE SKIP LOCKED, so every replica
--  polls concurrently and each claims a disjoint set — no leader election.
--
--  Deliberately Kafka-shaped, so adopting a broker later swaps the dispatcher
--  without changing event contracts.
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50)  NOT NULL COMMENT 'media | quota | upload',
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL
                                COMMENT 'media.created | media.deleted | media.purged | media.restored | quota.threshold.crossed | upload.session.expired',
    payload        JSON         NOT NULL
                                COMMENT 'IDENTIFIERS ONLY — never bytes, presigned URLs, or credentials',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING | IN_FLIGHT | DISPATCHED | FAILED',
    attempts       INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    dispatched_at  DATETIME(6)  NULL,
    last_error     VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_dispatch (status, next_retry_at, id),
    -- Dispatched rows are deleted after 7 days, or the outbox becomes the
    -- largest table in the schema within a year.
    KEY idx_outbox_cleanup  (status, dispatched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Transactional outbox; at-least-once delivery, DLQ after 10 attempts';


-- =============================================================================
--  6. AUDIT
--
--  APPEND-ONLY. The application's database role must hold no UPDATE or DELETE
--  grant on this table — a convention that can be violated is not an audit
--  trail. See the GRANT block at the end of this file.
-- =============================================================================

CREATE TABLE IF NOT EXISTS media_audit (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT       NOT NULL,
    project_id  BIGINT       NOT NULL,
    actor_id    VARCHAR(64)  NULL,
    actor_type  VARCHAR(20)  NOT NULL COMMENT 'USER | SERVICE | SYSTEM',
    action      VARCHAR(50)  NOT NULL
                             COMMENT 'UPLOAD_INITIATED | UPLOAD_COMPLETED | UPLOAD_ABORTED | MEDIA_DOWNLOADED | MEDIA_DELETED | MEDIA_RESTORED | MEDIA_PURGED | MEDIA_QUARANTINED | QUOTA_PROVISIONED | QUOTA_RECONCILED',
    resource_id VARCHAR(64)  NULL,
    detail      JSON         NULL,
    client_ip   VARCHAR(45)  NULL COMMENT 'IPv6-capable',
    trace_id    VARCHAR(64)  NULL,
    occurred_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_tenant_time (org_id, project_id, occurred_at DESC),
    KEY idx_audit_resource    (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only: who did what, when, from where';


-- =============================================================================
--  7. SCHEDULER LOCK
--
--  Every @Scheduled method in the predecessor ran on EVERY replica — invisible
--  at one instance, actively harmful at two, where reconciliation and cleanup
--  ran concurrently over the same rows.
--
--  Lease-based with an expiry, so an ungracefully terminated pod does not hold
--  a lock forever. In the database rather than Redis: the lock lives in the same
--  store as the data it guards, so it cannot be lost by an independent failure
--  (ADR-008).
-- =============================================================================

CREATE TABLE IF NOT EXISTS scheduler_lock (
    lock_name  VARCHAR(100) NOT NULL,
    locked_by  VARCHAR(100) NOT NULL,
    locked_at  DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Single-runner guarantee for scheduled jobs across replicas';


-- =============================================================================
--  GRANTS — least privilege
--
--  The audit table is append-only in fact, not just by convention. Adjust the
--  user and host to your deployment.
-- =============================================================================

-- CREATE USER IF NOT EXISTS 'storage_app'@'%' IDENTIFIED BY '<from-secrets-manager>';
--
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.org_storage        TO 'storage_app'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.project_storage    TO 'storage_app'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.media              TO 'storage_app'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.upload_session     TO 'storage_app'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.idempotency_record TO 'storage_app'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.outbox_event       TO 'storage_app'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON apargo_storage.scheduler_lock     TO 'storage_app'@'%';
--
-- -- Audit: INSERT and SELECT only. No UPDATE, no DELETE. Ever.
-- GRANT SELECT, INSERT ON apargo_storage.media_audit TO 'storage_app'@'%';
--
-- -- Flyway needs DDL; grant it to a SEPARATE migration user, not the app user.
-- -- GRANT ALL ON apargo_storage.* TO 'storage_migrator'@'%';
--
-- FLUSH PRIVILEGES;


-- =============================================================================
--  LOCAL SEED — development only. Never run in a shared environment.
-- =============================================================================

-- INSERT IGNORE INTO org_storage     (org_id, max_bytes, used_bytes) VALUES (1, 10737418240, 0);
-- INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes) VALUES (1, 1, 5368709120, 0);
