-- V2 — lifecycle, attribution, integrity. Additive only. docs/06 §2.

ALTER TABLE media
    ADD COLUMN created_by         BIGINT       NULL          AFTER project_id,
    ADD COLUMN deleted_by         BIGINT       NULL          AFTER deleted_at,
    ADD COLUMN checksum_sha256    CHAR(64)     NULL          AFTER file_size,
    ADD COLUMN detected_mime_type VARCHAR(100) NULL          AFTER mime_type,
    ADD COLUMN scan_status        VARCHAR(20)  NOT NULL DEFAULT 'SKIPPED' AFTER status,
    ADD COLUMN upload_session_id  CHAR(36)     NULL          AFTER scan_status,
    ADD COLUMN version            BIGINT       NOT NULL DEFAULT 0;

-- Rows predating this migration are, by definition, already committed objects.
UPDATE media SET status = 'ACTIVE' WHERE status IS NULL OR status = '';

-- Structurally prevents storage-key reuse. VARCHAR(512) so the index fits InnoDB's
-- 3072-byte limit under utf8mb4; real keys are around 60 characters.
ALTER TABLE media ADD CONSTRAINT uq_media_storage_key UNIQUE (storage_key);

-- Reaper and sweeper predicates.
CREATE INDEX idx_media_status_deleted_at ON media (status, deleted_at);
CREATE INDEX idx_media_status_created_at ON media (status, created_at);
CREATE INDEX idx_media_upload_session    ON media (upload_session_id);

-- Keyset pagination needs a total order: created_at alone is not unique.
CREATE INDEX idx_media_keyset      ON media (organisation_id, project_id, created_at DESC, id DESC);
CREATE INDEX idx_media_keyset_type ON media (organisation_id, project_id, media_type, created_at DESC, id DESC);

-- Neither has a query in this codebase; every unused index is write amplification.
DROP INDEX idx_media_org_project_created ON media;
DROP INDEX idx_media_org_project_type    ON media;
