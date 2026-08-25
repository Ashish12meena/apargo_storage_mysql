package com.aigreentick.services.storage.application.port.in;

/**
 * Background consistency work. Each method is idempotent and safe to run
 * concurrently with live traffic. Exposed as a use case so it can be triggered on
 * demand during an incident, and so the scheduler stays a thin trigger.
 */
public interface ReconcileStorageUseCase {

    /** RESERVED sessions past TTL → release quota, remove partial objects. */
    int sweepExpiredSessions();

    /**
     * Recompute {@code used_bytes} from {@code SUM(file_size)}.
     * Drift is reported as a metric and alerted on, not silently healed: non-zero
     * drift means a bug exists upstream of this job.
     */
    int reconcileQuotaUsage();

    /**
     * Objects in storage with no row. Undetectable in the predecessor, which sums
     * database rows — so an object with no row is invisible.
     */
    int reclaimOrphanedObjects();

    /** Removes dispatched outbox rows and expired idempotency records. */
    int purgeExpiredRecords();
}
