package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Maps {@code scheduler_lock}. Lease-based, so an ungraceful kill releases it. */
@Entity
@Table(name = "scheduler_lock")
public class SchedulerLockEntity {

    @Id
    @Column(name = "lock_name", nullable = false, length = 100)
    private String lockName;

    @Column(name = "locked_by", nullable = false, length = 100)
    private String lockedBy;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SchedulerLockEntity() {
    }

    public String getLockName() { return lockName; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
