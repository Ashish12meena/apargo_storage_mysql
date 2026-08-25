package com.aigreentick.services.storage.infrastructure.scheduler;

import com.aigreentick.services.storage.application.port.in.ReconcileStorageUseCase;
import com.aigreentick.services.storage.application.port.out.DistributedLockPort;
import com.aigreentick.services.storage.application.service.StorageReconciliationService;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.infrastructure.outbox.MediaReaper;
import com.aigreentick.services.storage.infrastructure.outbox.OutboxDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin triggers. All logic lives in the use cases, so it can also be invoked on
 * demand during an incident.
 *
 * <p>EVERY job runs under a distributed lock. Every {@code @Scheduled} method in
 * the predecessor ran on every replica — invisible at one instance, actively
 * harmful at two, where reconciliation and cleanup ran concurrently over the same
 * rows.
 *
 * <p>The outbox dispatcher is the deliberate exception: {@code FOR UPDATE SKIP
 * LOCKED} means parallel replicas share the work instead of colliding, so locking
 * it would throw away throughput for nothing.
 */
@Component
@Slf4j
public class StorageMaintenanceScheduler {

    private static final int PURGE_BATCH = 200;

    private final ReconcileStorageUseCase reconcile;
    private final StorageReconciliationService reconciliationService;
    private final OutboxDispatcher dispatcher;
    private final MediaReaper reaper;
    private final DistributedLockPort lock;

    public StorageMaintenanceScheduler(ReconcileStorageUseCase reconcile,
                                       StorageReconciliationService reconciliationService,
                                       OutboxDispatcher dispatcher, MediaReaper reaper,
                                       DistributedLockPort lock) {
        this.reconcile = reconcile;
        this.reconciliationService = reconciliationService;
        this.dispatcher = dispatcher;
        this.reaper = reaper;
        this.lock = lock;
    }

    /** Unlocked by design — see the class javadoc. */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    public void dispatchOutbox() {
        try {
            dispatcher.dispatchOnce();
        } catch (RuntimeException e) {
            log.error("outbox dispatch pass failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${quota.sweep-interval-ms:300000}")
    public void sweepExpiredSessions() {
        lock.runIfAcquired("sweep-sessions", Duration.ofMinutes(10), () -> {
            try {
                reconcile.sweepExpiredSessions();
            } catch (RuntimeException e) {
                log.error("session sweep failed", e);
            }
        });
    }

    /**
     * Purges rows whose grace period has elapsed. A wall-clock wait of days is a
     * scan, not an outbox retry — outbox backoff caps out in about thirty minutes.
     */
    @Scheduled(fixedDelayString = "${quota.purge-interval-ms:600000}")
    public void purgeDeletedMedia() {
        lock.runIfAcquired("purge-media", Duration.ofMinutes(30), () -> {
            try {
                List<Media> purgeable = reconciliationService.findPurgeable(PURGE_BATCH);
                for (Media media : purgeable) {
                    try {
                        reaper.purge(media);
                    } catch (RuntimeException e) {
                        // One bad object must not stop the batch; it is retried
                        // on the next pass.
                        log.warn("purge failed for media {}: {}", media.id(), e.toString());
                    }
                }
                if (!purgeable.isEmpty()) {
                    log.info("purged {} media object(s)", purgeable.size());
                }
            } catch (RuntimeException e) {
                log.error("purge scan failed", e);
            }
        });
    }

    @Scheduled(cron = "${quota.reconciliation-cron:0 0 3 * * *}")
    public void reconcileQuota() {
        lock.runIfAcquired("reconcile-quota", Duration.ofHours(2), () -> {
            try {
                int corrected = reconcile.reconcileQuotaUsage();
                if (corrected > 0) {
                    // Drift means a bug upstream of this job, so it is alertable,
                    // not merely healed.
                    log.warn("quota reconciliation corrected {} tenant(s)", corrected);
                }
            } catch (RuntimeException e) {
                log.error("quota reconciliation failed", e);
            }
        });
    }

    @Scheduled(cron = "${storage.orphan-scan-cron:0 30 3 * * *}")
    public void reclaimOrphans() {
        lock.runIfAcquired("reclaim-orphans", Duration.ofHours(2), () -> {
            try {
                int reclaimed = reconcile.reclaimOrphanedObjects();
                if (reclaimed > 0) {
                    log.warn("reclaimed {} orphaned object(s) — investigate the cause", reclaimed);
                }
            } catch (RuntimeException e) {
                log.error("orphan scan failed", e);
            }
        });
    }

    @Scheduled(cron = "${storage.retention-cron:0 0 4 * * *}")
    public void purgeExpiredRecords() {
        lock.runIfAcquired("purge-records", Duration.ofMinutes(30), () -> {
            try {
                reconcile.purgeExpiredRecords();
            } catch (RuntimeException e) {
                log.error("record retention pass failed", e);
            }
        });
    }
}
