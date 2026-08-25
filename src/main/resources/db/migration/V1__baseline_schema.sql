-- V1 — baseline. Reproduces the pre-existing live schema exactly so that
-- baseline-on-migrate is truthful. No DROP, no seed data. See docs/06 §1.

CREATE TABLE IF NOT EXISTS org_storage (
    org_id      BIGINT       NOT NULL,
    max_bytes   BIGINT       NOT NULL DEFAULT 0,
    used_bytes  BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (org_id),
    CONSTRAINT chk_org_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_org_max_non_negative  CHECK (max_bytes  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_storage (
    org_id      BIGINT       NOT NULL,
    project_id  BIGINT       NOT NULL,
    max_bytes   BIGINT       NOT NULL DEFAULT 0,
    used_bytes  BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (org_id, project_id),
    CONSTRAINT fk_project_storage_org FOREIGN KEY (org_id) REFERENCES org_storage (org_id),
    CONSTRAINT chk_proj_used_non_negative CHECK (used_bytes >= 0),
    CONSTRAINT chk_proj_max_non_negative  CHECK (max_bytes  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    organisation_id  BIGINT        NOT NULL,
    project_id       BIGINT        NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename  VARCHAR(255)  NULL,
    storage_key      VARCHAR(512)  NOT NULL,
    media_url        VARCHAR(2048) NULL,
    mime_type        VARCHAR(100)  NOT NULL,
    media_type       VARCHAR(20)   NOT NULL,
    file_size        BIGINT        NOT NULL,
    storage_provider VARCHAR(20)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_media_org_project_created (organisation_id, project_id, created_at DESC),
    KEY idx_media_org_project_type    (organisation_id, project_id, media_type, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
