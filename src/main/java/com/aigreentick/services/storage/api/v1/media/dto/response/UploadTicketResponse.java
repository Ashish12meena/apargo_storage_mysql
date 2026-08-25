package com.aigreentick.services.storage.api.v1.media.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** @param requiredHeaders must be echoed verbatim on the PUT; they are signed. */
public record UploadTicketResponse(
        String uploadId,
        String mediaId,
        String mode,
        List<String> urls,
        Map<String, String> requiredHeaders,
        long partSizeBytes,
        Instant expiresAt) {
}
