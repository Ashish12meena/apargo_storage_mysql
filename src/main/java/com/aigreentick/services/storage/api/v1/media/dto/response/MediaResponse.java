package com.aigreentick.services.storage.api.v1.media.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Wire representation of a media item.
 *
 * <p>{@code storedFilename} carries the media ID, NOT the storage key. The
 * predecessor returned the raw key, which made a leaked value sufficient for a
 * cross-tenant read. Retained only because downstream consumers may read the
 * field; removal is scheduled for v2.
 *
 * <p>{@code url} is short-lived and generated per response — never persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaResponse(
        String id,
        String originalFilename,
        @Deprecated String storedFilename,
        String contentType,
        String mediaType,
        long fileSizeBytes,
        String status,
        String checksumSha256,
        String url,
        Instant urlExpiresAt,
        Instant uploadedAt,
        String uploadedBy) {
}
