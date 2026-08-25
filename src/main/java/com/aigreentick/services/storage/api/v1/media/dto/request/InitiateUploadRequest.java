package com.aigreentick.services.storage.api.v1.media.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param sizeBytes exact size. Used for quota reservation and baked into the
 *                  presigned URL as a {@code content-length-range} condition, so a
 *                  client cannot under-declare to evade quota — storage itself
 *                  rejects the oversized PUT.
 */
public record InitiateUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @Size(max = 100) String declaredContentType,
        @Min(1) long sizeBytes) {
}
