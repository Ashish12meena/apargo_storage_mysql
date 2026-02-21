drop schema if exists apargo_storage_mysql;

create schema apargo_storage_mysql;

use apargo_storage_mysql;

CREATE TABLE media (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    -- File identity
    original_filename   VARCHAR(255)    NOT NULL,
    stored_filename     VARCHAR(255)    NOT NULL,
    mime_type           VARCHAR(100)    NOT NULL,
    file_size           BIGINT          NOT NULL,

    -- Media classification
    media_type          VARCHAR(50)     NOT NULL,

    -- Storage location
    storage_provider    VARCHAR(50)     NOT NULL,
    storage_key         VARCHAR(1000)   NOT NULL,
    storage_bucket      VARCHAR(255),
    storage_region      VARCHAR(100),
    media_url           VARCHAR(2048),

    -- WhatsApp / Facebook
    media_id            VARCHAR(255),
    waba_id             VARCHAR(255),

    -- Tenant context
    organisation_id     BIGINT          NOT NULL,
    project_id          BIGINT          NOT NULL,

    -- Lifecycle
    status              VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6),
    deleted_at          DATETIME(6),

    PRIMARY KEY (id),

    -- Core query patterns
    INDEX idx_media_org_project             (organisation_id, project_id),
    INDEX idx_media_org_project_type        (organisation_id, project_id, media_type),
    INDEX idx_media_org_project_created     (organisation_id, project_id, created_at DESC),
    INDEX idx_media_org_project_type_created(organisation_id, project_id, media_type, created_at DESC),

    -- Lookup patterns
    INDEX idx_media_media_id        (media_id),
    INDEX idx_media_stored_filename (stored_filename),
    INDEX idx_media_status          (status)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



-- ============================================================================
-- Organisation-level storage quota
-- One row per organisation. Tracks total limit and aggregate usage.
-- ============================================================================
CREATE TABLE org_storage (
    org_id          BIGINT          NOT NULL,
    max_bytes       BIGINT          NOT NULL DEFAULT 0,
    used_bytes      BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (org_id),

    CONSTRAINT chk_org_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_org_max_non_negative  CHECK (max_bytes  >= 0)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================================
-- Project-level storage quota
-- One row per (org, project). Tracks per-project limit and usage.
-- ============================================================================
CREATE TABLE project_storage (
    org_id          BIGINT          NOT NULL,
    project_id      BIGINT          NOT NULL,
    max_bytes       BIGINT          NOT NULL DEFAULT 0,
    used_bytes      BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (org_id, project_id),

    CONSTRAINT fk_project_storage_org
        FOREIGN KEY (org_id) REFERENCES org_storage (org_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_proj_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_proj_max_non_negative  CHECK (max_bytes  >= 0),

    INDEX idx_project_storage_project_id (project_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


ALTER TABLE org_storage ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE project_storage ADD COLUMN version BIGINT NOT NULL DEFAULT 0;


-- V3__seed_quota_data.sql
-- Seed quota data for development/testing.
-- Adjust org_id and project_id to match your X-Org-Id / X-Project-Id headers.

-- ── Org-level quotas ────────────────────────────────────────────────────────
-- 1 GB per org
INSERT INTO org_storage (org_id, max_bytes, used_bytes) VALUES
    (1, 1073741824, 0),   -- Org 1: 1 GB limit
    (2, 2147483648, 0);   -- Org 2: 2 GB limit

-- ── Project-level quotas ────────────────────────────────────────────────────
-- Each project gets a portion of the org's total
INSERT INTO project_storage (org_id, project_id, max_bytes, used_bytes) VALUES
    (1, 1, 524288000, 0),    -- Org1, Project1: 500 MB
    (1, 2, 524288000, 0),    -- Org1, Project2: 500 MB
    (2, 3, 1073741824, 0),   -- Org2, Project3: 1 GB
    (2, 4, 1073741824, 0);   -- Org2, Project4: 1 GB
