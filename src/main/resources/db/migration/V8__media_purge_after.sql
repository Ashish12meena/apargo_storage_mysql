-- V8 — separate WHEN a row was deleted from WHEN it may be purged.
--
-- deleted_at is a fact: the moment the delete happened. It belongs to the audit
-- record and must never be moved.
--
-- purge_after is a POLICY: the moment the object becomes removable. A routine
-- delete sets it to now + grace; a compliance erasure sets it to now; a tenant
-- teardown may set either.
--
-- Without this split, `?permanent=true` had nowhere to record its intent, so the
-- reaper applied the grace period to every delete regardless — the parameter was
-- accepted and then silently ignored.

ALTER TABLE media
    ADD COLUMN purge_after DATETIME(6) NULL AFTER deleted_by;

-- Backfill: existing deleted rows keep their effective behaviour (7-day grace).
UPDATE media
   SET purge_after = DATE_ADD(deleted_at, INTERVAL 7 DAY)
 WHERE status = 'DELETED' AND deleted_at IS NOT NULL AND purge_after IS NULL;

-- The reaper's only query.
CREATE INDEX idx_media_purge_after ON media (status, purge_after);
DROP INDEX idx_media_status_deleted_at ON media;
