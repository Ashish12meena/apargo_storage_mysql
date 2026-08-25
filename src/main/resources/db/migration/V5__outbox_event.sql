-- V5 — transactional outbox. Kafka-shaped so the later swap is a dispatcher
-- change, not a contract change. docs/adr/ADR-006.

CREATE TABLE outbox_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    dispatched_at  DATETIME(6)  NULL,
    last_error     VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_dispatch (status, next_retry_at, id),
    KEY idx_outbox_cleanup  (status, dispatched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
