package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.command.DeleteMediaCommand;
import com.aigreentick.services.storage.application.port.in.command.RestoreMediaCommand;
import com.aigreentick.services.storage.application.port.in.DeleteMediaUseCase;
import com.aigreentick.services.storage.application.port.out.AuditPort;
import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.domain.exception.IllegalMediaStateException;
import com.aigreentick.services.storage.domain.exception.MediaNotFoundException;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.shared.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Soft delete, quota release, and event emission — all in one transaction.
 * Physical removal happens later, in the reaper.
 *
 * <p>Storage is never touched here. If this transaction rolled back after the
 * object was gone, the row would point at nothing, unrecoverably.
 */
@Service
@Slf4j
public class MediaDeletionService implements DeleteMediaUseCase {

    private final MediaRepositoryPort mediaRepository;
    private final QuotaApplicationService quotaService;
    private final OutboxPort outbox;
    private final AuditPort audit;
    private final ClockPort clock;
    private final QuotaProperties quotaProperties;
    private final MediaViewMapper viewMapper;

    public MediaDeletionService(MediaRepositoryPort mediaRepository, QuotaApplicationService quotaService,
                                OutboxPort outbox, AuditPort audit, ClockPort clock,
                                QuotaProperties quotaProperties, MediaViewMapper viewMapper) {
        this.mediaRepository = mediaRepository;
        this.quotaService = quotaService;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.quotaProperties = quotaProperties;
        this.viewMapper = viewMapper;
    }

    @Override
    @Transactional
    public void delete(DeleteMediaCommand command) {
        Optional<Media> found = mediaRepository.findByIdForTenant(command.mediaId(), command.tenant());
        if (found.isEmpty()) {
            // Deleting an absent item is a success: DELETE must be idempotent, and
            // a 404 here would also confirm non-existence to a probing caller.
            return;
        }
        Media media = found.get();
        if (media.status() == MediaStatus.DELETED || media.status() == MediaStatus.PURGED) {
            return;
        }

        Instant now = clock.now();
        Long actorId = command.actor() == null ? null : command.actor().userIdAsLong();

        // permanent=true now actually means immediate. Previously the parameter was
        // accepted, the scope was checked, and the reaper applied the grace period
        // anyway, because there was nowhere to record the intent.
        Instant purgeAfter = command.permanent()
                ? now
                : now.plus(quotaProperties.deleteGracePeriod());

        // Conditional update: the loser of a concurrent delete sees 0 rows and
        // skips the release, so quota is never refunded twice.
        int rows = mediaRepository.transitionStatus(command.mediaId(), command.tenant(),
                MediaStatus.ACTIVE, MediaStatus.DELETED, actorId, now, purgeAfter);
        if (rows == 0) {
            log.debug("concurrent delete lost the race for media {}", command.mediaId());
            return;
        }

        quotaService.release(command.tenant(), media.billableSize());

        outbox.append(new DomainEvent.MediaDeleted(media.id().asString(), command.tenant().orgId(),
                command.tenant().projectId(), media.storageKey().value(), media.billableSize().value(),
                command.permanent(), now));

        audit.record(command.tenant(), command.actor(), AuditPort.AuditAction.MEDIA_DELETED,
                media.id().asString(), "{\"permanent\":" + command.permanent() + "}");
    }

    @Override
    @Transactional
    public MediaView restore(RestoreMediaCommand command) {
        Media media = mediaRepository.findByIdForTenant(command.mediaId(), command.tenant())
                .orElseThrow(() -> new MediaNotFoundException(
                        "media " + command.mediaId() + " not found for " + command.tenant()));

        Instant now = clock.now();
        if (!media.isRestorableAt(now)) {
            throw new IllegalMediaStateException("media " + command.mediaId()
                    + " is not restorable (status=" + media.status() + ")");
        }

        // Re-charge quota BEFORE flipping status: if the tenant no longer has room,
        // the restore must fail rather than push them over their limit.
        quotaService.reserveOrThrow(command.tenant(), media.billableSize());

        Long actorId = command.actor() == null ? null : command.actor().userIdAsLong();
        int rows = mediaRepository.transitionStatus(command.mediaId(), command.tenant(),
                MediaStatus.DELETED, MediaStatus.ACTIVE, actorId, now, null);
        if (rows == 0) {
            quotaService.release(command.tenant(), media.billableSize());
            throw new IllegalMediaStateException("media " + command.mediaId() + " changed state concurrently");
        }

        media.restore(now);
        outbox.append(new DomainEvent.MediaRestored(media.id().asString(), command.tenant().orgId(),
                command.tenant().projectId(), now));
        audit.record(command.tenant(), command.actor(), AuditPort.AuditAction.MEDIA_RESTORED,
                media.id().asString(), null);
        return viewMapper.toView(media);
    }

    /** Used by the deletion policy: has this row's purge window opened? */
    public boolean isPurgeable(Media media, Instant now) {
        return media.isPurgeableAt(now);
    }

    /** Convenience for the internal teardown paths. */
    public Actor systemActor(String job) {
        return Actor.system(job);
    }
}
