package com.aigreentick.services.storage.domain.upload;

import java.util.UUID;

/** Opaque, client-visible handle for a two-phase upload. */
public record UploadSessionId(String value) {

    public UploadSessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("upload session id must not be blank");
        }
    }

    public static UploadSessionId generate() {
        return new UploadSessionId(UUID.randomUUID().toString());
    }

    public static UploadSessionId of(String value) {
        return new UploadSessionId(value);
    }
}
