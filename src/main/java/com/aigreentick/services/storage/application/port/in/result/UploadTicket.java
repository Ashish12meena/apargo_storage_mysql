package com.aigreentick.services.storage.application.port.in.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of initiating a direct upload.
 *
 * <p>{@code requiredHeaders} must be echoed verbatim by the client: they are part
 * of the signature and constrain the upload to the exact declared size and type.
 */
public record UploadTicket(
        String uploadSessionId,
        String mediaId,
        String mode,
        List<String> urls,
        Map<String, String> requiredHeaders,
        long partSizeBytes,
        Instant expiresAt) {
}
