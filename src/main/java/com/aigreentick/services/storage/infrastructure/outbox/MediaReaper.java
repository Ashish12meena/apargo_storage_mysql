package com.aigreentick.services.storage.infrastructure.outbox;

import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.EventPublisherPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@code media.deleted}: removes the stored object, then marks the row
 * {@code PURGED}.
 *
 * <p>This ordering is the only safe one. Marking {@code PURGED} before the delete
 * succeeds would lose the record of what still needs removing; deleting inside the
 * API transaction would risk a rollback after the object is already gone.
 */
@Component
@Slf4j
public class MediaReaper implements EventPublisherPort {

    private final StoragePort storage;
    private final MediaRepositoryPort mediaRepository;
    private final QuotaProperties quotaProperties;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;

    public MediaReaper(StoragePort storage, MediaRepositoryPort mediaRepository,
                       QuotaProperties quotaProperties, ClockPort clock, ObjectMapper objectMapper) {
        this.storage = storage;
        this.mediaRepository = mediaRepository;
        this.quotaProperties = quotaProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return "media.deleted";
    }

    @Override
    public void handle(OutboxPort.OutboxRecord record) {
        JsonNode payload = parse(record.payloadJson());
        boolean permanent = payload.path("permanent").asBoolean(false);
        String storageKeyValue = payload.path("storageKey").asText(null);
        long mediaIdValue = Long.parseLong(record.aggregateId());

        Optional<Media> found = mediaRepository.findByIdForMaintenance(MediaId.of(mediaIdValue));
        if (found.isEmpty()) {
            log.warn("media {} referenced by outbox event no longer exists", mediaIdValue);
            return;
        }
        Media media = found.get();

        if (media.status() == MediaStatus.PURGED) {
            return; // Idempotent: already done.
        }
        if (media.status() != MediaStatus.DELETED) {
            // Restored inside the grace period. Nothing to purge.
            log.info("media {} is {}, skipping purge", mediaIdValue, media.status());
            return;
        }

        Instant now = clock.now();
        if (!permanent && !media.isPurgeableAt(now)) {
            // Still recoverable, so ACKNOWLEDGE and hand off to MediaPurgeJob.
            //
            // Retrying instead would be wrong: outbox backoff caps at ~30 minutes
            // over 10 attempts, so a 7-day grace period would dead-letter every
            // ordinary delete. The scheduled scan is the right mechanism for a
            // long, wall-clock-driven wait.
            log.debug("media {} still within grace period; purge deferred to the scan", mediaIdValue);
            return;
        }

        StorageKey key = storageKeyValue == null ? media.storageKey() : new StorageKey(storageKeyValue);
        storage.delete(key);          // absent key counts as success, so retries converge
        mediaRepository.markPurgedForMaintenance(media.id(), now);

        log.info("purged media {} from {}", mediaIdValue, storage.providerType());
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("unreadable outbox payload", e);
        }
    }

    /**
     * Purges one row. Shared with {@code MediaPurgeJob}, which finds rows whose
     * grace period has elapsed.
     */
    public void purge(Media media) {
        storage.delete(media.storageKey());   // absent key is success, so retries converge
        mediaRepository.markPurgedForMaintenance(media.id(), clock.now());
        log.info("purged media {} from {}", media.id(), storage.providerType());
    }
}
