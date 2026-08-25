package com.aigreentick.services.storage.application.port.in.result;

import java.time.Instant;

/**
 * Read model for a media item.
 *
 * <p>Carries NO storage key. The predecessor returned it as {@code storedFilename}
 * and accepted it back on {@code /public-url} with no ownership check, which is
 * what made a leaked key a cross-tenant read.
 */
public record MediaView(
        String id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String mediaType,
        String status,
        String checksumSha256,
        String downloadUrl,
        Instant downloadUrlExpiresAt,
        Instant createdAt,
        String createdBy) {
}
