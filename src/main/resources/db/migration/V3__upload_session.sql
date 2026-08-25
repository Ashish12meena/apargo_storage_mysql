-- V3 — two-phase quota reservation. docs/05 §5, docs/06 §4.1.

CREATE TABLE upload_session (
    id                 CHAR(36)      NOT NULL,
    org_id             BIGINT        NOT NULL,
    project_id         BIGINT        NOT NULL,
    media_id           BIGINT        NULL,
    storage_key        VARCHAR(512)  NOT NULL,
    mode               VARCHAR(30)   NOT NULL,
    declared_size      BIGINT        NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    idempotency_key    VARCHAR(255)  NULL,
    provider_upload_id VARCHAR(255)  NULL,
    created_at         DATETIME(6)   NOT NULL,
    expires_at         DATETIME(6)   NOT NULL,
    completed_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_session_sweep  (status, expires_at),
    KEY idx_session_tenant (org_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
