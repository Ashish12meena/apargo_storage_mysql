package com.aigreentick.services.storage.common.context;

import java.time.Instant;

/**
 * Per-request ambient data. Deliberately excludes tenant identity: that is a
 * security decision and lives in {@code api.security.TenantPrincipal}, where its
 * only source can be a verified credential.
 */
public record RequestContextData(String traceId, String requestId, String clientIp, Instant receivedAt) {
}
