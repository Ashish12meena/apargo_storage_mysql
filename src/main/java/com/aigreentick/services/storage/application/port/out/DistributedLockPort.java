package com.aigreentick.services.storage.application.port.out;

import java.time.Duration;

/**
 * Single-runner guarantee for scheduled jobs.
 *
 * <p>Every {@code @Scheduled} method in the predecessor ran on every replica —
 * invisible at one instance, actively harmful at two, where quota reconciliation
 * and file cleanup would run concurrently over the same rows.
 *
 * <p>Backed by a database row rather than Redis: the lock lives in the same store
 * as the data it guards, so it cannot be lost by an independent failure (ADR-008).
 */
public interface DistributedLockPort {

    boolean tryAcquire(String lockName, Duration leaseDuration);

    void release(String lockName);

    /** Runs the task only if the lock is acquired. Returns false if skipped. */
    boolean runIfAcquired(String lockName, Duration leaseDuration, Runnable task);
}
