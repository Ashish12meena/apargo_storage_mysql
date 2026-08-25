-- V6 — append-only audit. The application role must hold no UPDATE or DELETE
-- grant on this table; a convention that can be violated is not an audit trail.

CREATE TABLE media_audit (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT       NOT NULL,
    project_id  BIGINT       NOT NULL,
    actor_id    VARCHAR(64)  NULL,
    actor_type  VARCHAR(20)  NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    resource_id VARCHAR(64)  NULL,
    detail      JSON         NULL,
    client_ip   VARCHAR(45)  NULL,
    trace_id    VARCHAR(64)  NULL,
    occurred_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_tenant_time (org_id, project_id, occurred_at DESC),
    KEY idx_audit_resource    (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
