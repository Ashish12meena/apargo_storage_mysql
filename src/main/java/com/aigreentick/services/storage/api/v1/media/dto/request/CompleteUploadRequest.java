package com.aigreentick.services.storage.api.v1.media.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** @param parts multipart only: per-part ETags returned by storage, in order. */
public record CompleteUploadRequest(List<PartETag> parts) {

    public CompleteUploadRequest {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public record PartETag(@Min(1) int partNumber, @NotBlank String etag) {
    }
}
