-- V4 — retry safety. Tenant-scoped PK so keys cannot collide or be probed
-- across orgs. docs/05 §6.3.

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
    KEY idx_idem_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
