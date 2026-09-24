-- ═══════════════════════════════════════════════════════════════════════════
-- storage-service — FULL RESET (drop everything)
--
-- DESTRUCTIVE. Drops every table this service owns, including the Flyway
-- history, so the next boot re-runs V1..V8 from scratch. Intended for local
-- and for rebuilding a broken environment, never for production.
--
-- Stored objects are NOT touched. Dropping `media` removes the rows that
-- point at files on disk or in S3; the files themselves stay. Clear the
-- storage root separately or you will be left with orphans that no sweeper
-- knows about:
--     rm -rf ./media-uploads/*          (local provider)
--     aws s3 rm s3://<bucket>/ --recursive
--
-- Usage:
--     mysql -u <user> -p <database> < reset_drop.sql
-- ═══════════════════════════════════════════════════════════════════════════

-- Only ONE foreign key exists in this schema (project_storage -> org_storage),
-- and the drop order below already respects it. The guard is here because
-- DROP order stops mattering the moment someone adds a second FK, and a reset
-- script that fails halfway leaves a half-dropped schema that is worse than
-- either state. Restored at the end rather than left off.
SET FOREIGN_KEY_CHECKS = 0;

-- ── Leaf tables: no dependants, no dependencies ────────────────────────────
DROP TABLE IF EXISTS scheduler_lock;      -- V7
DROP TABLE IF EXISTS media_audit;         -- V6  (append-only audit trail)
DROP TABLE IF EXISTS outbox_event;        -- V5
DROP TABLE IF EXISTS idempotency_record;  -- V4
DROP TABLE IF EXISTS upload_session;      -- V3

-- ── Media ──────────────────────────────────────────────────────────────────
-- References tenants by id only, with no FK, so it is not constrained here.
-- Dropped before the quota tables anyway to keep the order readable as
-- "most dependent first".
DROP TABLE IF EXISTS media;               -- V1 + V2 + V8

-- ── Quota, child before parent ─────────────────────────────────────────────
-- project_storage holds fk_project_storage_org -> org_storage(org_id).
-- This is the one pair whose order is load-bearing.
DROP TABLE IF EXISTS project_storage;     -- V1
DROP TABLE IF EXISTS org_storage;         -- V1

-- ── Flyway ─────────────────────────────────────────────────────────────────
-- Without this the schema is empty but Flyway still believes V1..V8 ran, and
-- the service boots against no tables at all. Dropping it is what makes the
-- reset actually complete.
DROP TABLE IF EXISTS flyway_schema_history;

SET FOREIGN_KEY_CHECKS = 1;

-- Should return zero rows. If anything is listed, it was created outside the
-- migrations and this script does not know about it.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('org_storage', 'project_storage', 'media', 'upload_session',
                     'idempotency_record', 'outbox_event', 'media_audit',
                     'scheduler_lock', 'flyway_schema_history');