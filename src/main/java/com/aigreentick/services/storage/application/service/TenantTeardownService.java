package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.command.TeardownTenantCommand;
import com.aigreentick.services.storage.application.port.in.TeardownTenantUseCase;
import com.aigreentick.services.storage.application.port.out.AuditPort;
import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.port.out.QuotaRepositoryPort;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Tenant offboarding.
 *
 * <p>Accepting the request is synchronous and cheap: one outbox row. The work
 * itself runs in bounded batches through {@code TenantTeardownHandler}, because an
 * organisation teardown can span millions of objects and must not run inside a
 * request.
 *
 * <p>Unlike the predecessor's {@code deleteByOrgAndProject}, this releases quota
 * and removes the stored objects.
 */
@Service
@Slf4j
public class TenantTeardownService implements TeardownTenantUseCase {

    private final MediaRepositoryPort mediaRepository;
    private final QuotaRepositoryPort quotaRepository;
    private final OutboxPort outbox;
    private final AuditPort audit;
    private final ClockPort clock;
    private final QuotaProperties quotaProperties;

    public TenantTeardownService(MediaRepositoryPort mediaRepository, QuotaRepositoryPort quotaRepository,
                                 OutboxPort outbox, AuditPort audit, ClockPort clock,
                                 QuotaProperties quotaProperties) {
        this.mediaRepository = mediaRepository;
        this.quotaRepository = quotaRepository;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.quotaProperties = quotaProperties;
    }

    @Override
    @Transactional
    public String requestTeardown(TeardownTenantCommand command) {
        String handle = UUID.randomUUID().toString();
        Instant now = clock.now();

        long liveCount = mediaRepository.countLiveForMaintenance(command.orgId(), command.projectId());

        outbox.append(new DomainEvent.TenantTeardownRequested(handle, command.orgId(),
                command.projectId(), command.permanent(),
                command.actor() == null ? "unknown" : command.actor().userId(), 0, 0L, now));

        audit.record(new TenantRef(command.orgId(), command.projectId() == null ? 1L : command.projectId()),
                command.actor(), AuditPort.AuditAction.MEDIA_DELETED, handle,
                "{\"teardown\":true,\"scope\":\"" + (command.isWholeOrg() ? "ORG" : "PROJECT")
                        + "\",\"permanent\":" + command.permanent()
                        + ",\"estimatedFiles\":" + liveCount + "}");

        log.warn("TENANT TEARDOWN requested: handle={} org={} project={} permanent={} files~{} by={}",
                handle, command.orgId(),
                command.projectId() == null ? "ALL" : command.projectId(),
                command.permanent(), liveCount,
                command.actor() == null ? "unknown" : command.actor().userId());
        return handle;
    }

    /**
     * Marks one bounded batch. Returns rows affected; zero means the teardown is
     * complete.
     *
     * <p>Storage is not touched here. Rows become {@code DELETED} with a
     * {@code purge_after}, and the existing purge scan removes the objects — the
     * same path a single-file delete takes, so there is one reaper and one set of
     * failure modes rather than two.
     */
    @Transactional
    public int teardownBatch(long orgId, Long projectId, boolean permanent, String actorId) {
        Instant now = clock.now();
        Instant purgeAfter = permanent ? now : now.plus(quotaProperties.deleteGracePeriod());
        Long actorUserId = parseActor(actorId);

        return mediaRepository.softDeleteTenantBatchForMaintenance(
                orgId, projectId, actorUserId, now, purgeAfter, quotaProperties.teardownBatchSize());
    }

    /**
     * Recomputes quota once no live rows remain.
     *
     * <p>Recompute rather than decrement-per-row: after teardown the correct usage
     * is whatever the surviving rows sum to, and deriving it is immune to a batch
     * having been retried.
     */
    @Transactional
    public void settleQuota(long orgId, Long projectId) {
        if (projectId != null) {
            recompute(new TenantRef(orgId, projectId));
            return;
        }
        for (TenantRef tenant : mediaRepository.findTenantsForOrgForMaintenance(orgId)) {
            recompute(tenant);
        }
    }

    private void recompute(TenantRef tenant) {
        long actual = mediaRepository.sumActiveBytesForMaintenance(tenant);
        quotaRepository.correctUsage(tenant, ByteSize.of(actual));
        log.info("teardown settled quota for {} to {} bytes", tenant, actual);
    }

    private Long parseActor(String actorId) {
        if (actorId == null) {
            return null;
        }
        try {
            return Long.valueOf(actorId);
        } catch (NumberFormatException e) {
            return null;   // service callers have non-numeric ids
        }
    }
}
