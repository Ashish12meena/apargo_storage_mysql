package com.aigreentick.services.storage.api.v1.media.dto.response;

import java.time.Instant;

/** A short-lived, tenant-scoped download URL. Never persist it. */
public record DownloadUrlResponse(String url, Instant expiresAt) {
}
