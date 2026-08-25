package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.ReconcileStorageUseCase;
import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.IdempotencyPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.port.out.QuotaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.application.port.out.UploadSessionPort;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.domain.upload.UploadSession;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Self-healing background work.
 *
 * <p>Every recovery path in this service resolves here or in the outbox reaper —
 * never in a catch block, because a catch block does not run when the process
 * dies, which is exactly when recovery is needed.
 */
@Service
@Slf4j
public class StorageReconciliationService implements ReconcileStorageUseCase {

    private static final int BATCH = 200;
    private static final Duration ORPHAN_MIN_AGE = Duration.ofHours(24);

    private final UploadSessionPort sessionRepository;
    private final MediaRepositoryPort mediaRepository;
    private final QuotaRepositoryPort quotaRepository;
    private final StoragePort storage;
    private final OutboxPort outbox;
    private final IdempotencyPort idempotency;
    private final ClockPort clock;
    private final QuotaProperties quotaProperties;
    private final MeterRegistry meters;
    private final TransactionTemplate transactionTemplate;
    private final Map<TenantRef, AtomicLong> driftGauges = new ConcurrentHashMap<>();

    public StorageReconciliationService(UploadSessionPort sessionRepository, MediaRepositoryPort mediaRepository,
                                        QuotaRepositoryPort quotaRepository, StoragePort storage,
                                        OutboxPort outbox, IdempotencyPort idempotency, ClockPort clock,
                                        QuotaProperties quotaProperties, MeterRegistry meters,
                                        TransactionTemplate transactionTemplate) {
        this.sessionRepository = sessionRepository;
        this.mediaRepository = mediaRepository;
        this.quotaRepository = quotaRepository;
        this.storage = storage;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.clock = clock;
        this.quotaProperties = quotaProperties;
        this.meters = meters;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public int sweepExpiredSessions() {
        Instant now = clock.now();
        List<UploadSession> reclaimable = sessionRepository.findReclaimableForMaintenance(now, BATCH);
        int swept = 0;
        for (UploadSession session : reclaimable) {
            try {
                // Storage first: if this fails we retry next pass, having changed
                // nothing. Releasing quota first would risk a double release.
                storage.delete(session.storageKey());
                transactionTemplate.executeWithoutResult(status -> {
                    quotaRepository.release(session.tenant(), session.declaredSize());
                    session.expire(now);
                    sessionRepository.save(session);
                    if (session.mediaId() != null) {
                        mediaRepository.findByIdForMaintenance(session.mediaId())
                                .filter(m -> m.status() == MediaStatus.PENDING)
                                .ifPresent(m -> {
                                    m.expire(now);
                                    mediaRepository.save(m);
                                });
                    }
                    outbox.append(new DomainEvent.UploadSessionExpired(session.id().value(),
                            session.tenant().orgId(), session.tenant().projectId(),
                            session.storageKey().value(), session.declaredSize().value(), now));
                });
                swept++;
                meters.counter("storage.session.expired").increment();
            } catch (RuntimeException e) {
                log.warn("failed to sweep session {}: {}", session.id().value(), e.toString());
            }
        }
        if (swept > 0) {
            log.info("swept {} expired upload sessions", swept);
        }
        return swept;
    }

    @Override
    public int reconcileQuotaUsage() {
        if (!quotaProperties.reconciliationEnabled()) {
            return 0;
        }
        List<TenantRef> tenants = mediaRepository.findDistinctTenantsForMaintenance();
        int corrected = 0;
        for (TenantRef tenant : tenants) {
            try {
                long actual = mediaRepository.sumActiveBytesForMaintenance(tenant);
                long recorded = quotaRepository.findProjectQuota(tenant)
                        .map(q -> q.used().value()).orElse(-1L);
                if (recorded < 0) {
                    continue;
                }
                long drift = recorded - actual;

                // Drift is reported, not silently healed: a non-zero value means a
                // bug exists upstream of this job.
                //
                // The holder is registered ONCE per tenant and then mutated.
                // Re-registering a gauge with the same name and tags returns the
                // existing meter and discards the new object, so passing a fresh
                // AtomicLong each pass would freeze the value at its first reading.
                driftGauges.computeIfAbsent(tenant, t -> meters.gauge("storage.quota.drift.bytes",
                        Tags.of("org_id", String.valueOf(t.orgId()),
                                "project_id", String.valueOf(t.projectId())),
                        new AtomicLong(0), AtomicLong::doubleValue)).set(drift);

                if (drift != 0) {
                    log.warn("QUOTA DRIFT tenant={} recorded={} actual={} drift={}",
                            tenant, recorded, actual, drift);
                    quotaRepository.correctUsage(tenant, ByteSize.of(actual));
                    corrected++;
                }
            } catch (RuntimeException e) {
                log.error("reconciliation failed for {}", tenant, e);
            }
        }
        return corrected;
    }

    @Override
    public int reclaimOrphanedObjects() {
        Instant cutoff = clock.now().minus(ORPHAN_MIN_AGE);
        int reclaimed = 0;
        for (TenantRef tenant : mediaRepository.findDistinctTenantsForMaintenance()) {
            String cursor = null;
            do {
                StoragePort.KeyPage page = storage.listKeys(tenant.storagePrefix(), cursor, BATCH);
                for (StorageKey key : page.keys()) {
                    // A key with no row at all is an orphan. Age gate avoids racing
                    // an in-flight upload whose row exists but is still PENDING.
                    if (!mediaRepository.existsByStorageKeyForMaintenance(key)
                            && isOlderThan(key, cutoff)) {
                        storage.delete(key);
                        reclaimed++;
                        meters.counter("storage.orphans.reclaimed",
                                "provider", storage.providerType().name()).increment();
                        log.warn("reclaimed orphaned object under {}", tenant.storagePrefix());
                    }
                }
                cursor = page.nextCursor();
            } while (cursor != null);
        }
        return reclaimed;
    }

    private boolean isOlderThan(StorageKey key, Instant cutoff) {
        return storage.head(key)
                .map(o -> o.lastModified() != null && o.lastModified().isBefore(cutoff))
                .orElse(false);
    }

    @Override
    @Transactional
    public int purgeExpiredRecords() {
        Instant now = clock.now();
        int outboxRows = outbox.deleteDispatchedBefore(now.minus(Duration.ofDays(7)));
        int idemRows = idempotency.deleteExpiredBefore(now);
        return outboxRows + idemRows;
    }

    /** Exposed for the reaper: rows whose grace period has elapsed. */
    public List<Media> findPurgeable(int limit) {
        // Driven by purge_after, so a compliance erasure is picked up immediately
        // while a routine delete waits out its grace period.
        return mediaRepository.findPurgeableForMaintenance(clock.now(), limit);
    }
}
