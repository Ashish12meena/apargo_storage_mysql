package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.domain.media.Media;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain → read model.
 *
 * <p>The download URL is generated per response and never persisted. The
 * predecessor stored {@code media_url}, which for a presigned URL is already stale
 * when it is written.
 */
@Component
@Slf4j
public class MediaViewMapper {

    private final StoragePort storage;
    private final StorageProperties properties;

    public MediaViewMapper(StoragePort storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public MediaView toView(Media media) {
        Duration ttl = Duration.ofMinutes(properties.s3().presignExpiryMinutes());
        return toView(media, ttl);
    }

    public MediaView toView(Media media, Duration urlTtl) {
        String url = null;
        Instant expiresAt = null;
        if (media.isReadable()) {
            try {
                url = storage.presignGet(media.storageKey(), urlTtl);
                expiresAt = Instant.now().plus(urlTtl);
            } catch (RuntimeException e) {
                // A URL we cannot mint is omitted, not faked. The predecessor fell
                // back to an unsigned URL for a private object — a guaranteed 403
                // that reaches the user as a broken file.
                log.warn("could not presign read URL for media {}: {}", media.id(), e.toString());
            }
        }
        return new MediaView(
                media.id() == null ? null : media.id().asString(),
                media.originalFilename(),
                media.contentType() == null ? null : media.contentType().detected(),
                media.billableSize().value(),
                media.mediaType() == null ? null : media.mediaType().name(),
                media.status().name(),
                media.checksum() == null ? null : media.checksum().sha256Hex(),
                url,
                expiresAt,
                media.createdAt(),
                media.createdBy() == null ? null : String.valueOf(media.createdBy()));
    }

    /** Metadata only, no presigned URL. Used where a URL would be wasted work. */
    public MediaView toViewWithoutUrl(Media media) {
        return new MediaView(
                media.id() == null ? null : media.id().asString(),
                media.originalFilename(),
                media.contentType() == null ? null : media.contentType().detected(),
                media.billableSize().value(),
                media.mediaType() == null ? null : media.mediaType().name(),
                media.status().name(),
                media.checksum() == null ? null : media.checksum().sha256Hex(),
                null, null,
                media.createdAt(),
                media.createdBy() == null ? null : String.valueOf(media.createdBy()));
    }
}
