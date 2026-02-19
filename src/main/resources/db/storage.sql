
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
    checksum            VARCHAR(64)     NOT NULL,         -- SHA-256 for duplicate detection

    -- Media classification
    media_type          VARCHAR(50)     NOT NULL,         -- IMAGE, VIDEO, DOCUMENT, AUDIO, PRODUCT

    -- Storage location
    storage_provider    VARCHAR(50)     NOT NULL,         -- LOCAL, S3, MINIO, etc.
    storage_key         VARCHAR(1000)   NOT NULL,
    storage_bucket      VARCHAR(255),
    storage_region      VARCHAR(100),
    media_url           VARCHAR(2048),

    -- WhatsApp / Facebook
    media_id            VARCHAR(255),                     -- WhatsApp media_id returned after upload
    waba_id             VARCHAR(255),

    -- Tenant context
    organisation_id     BIGINT          NOT NULL,
    project_id          BIGINT          NOT NULL,

    -- Lifecycle
    status              VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, DELETED
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6),
    deleted_at          DATETIME(6),

    PRIMARY KEY (id),

    -- Duplicate detection: same file in same org+project
    UNIQUE INDEX idx_media_checksum_org_project (checksum, organisation_id, project_id),

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