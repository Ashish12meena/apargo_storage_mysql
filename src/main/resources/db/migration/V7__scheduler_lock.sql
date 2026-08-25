-- V7 — single-runner guarantee for scheduled jobs. Lease-based, so an
-- ungracefully terminated pod does not hold a lock forever. docs/adr/ADR-008.

CREATE TABLE scheduler_lock (
    lock_name  VARCHAR(100) NOT NULL,
    locked_by  VARCHAR(100) NOT NULL,
    locked_at  DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
