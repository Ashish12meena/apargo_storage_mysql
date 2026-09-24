package com.aigreentick.services.storage.common.context;

import java.time.Instant;

/**
 * Per-request ambient data. Deliberately excludes tenant identity: that is a
 * security decision and lives in {@code api.security.TenantPrincipal}, where its
 * only source can be an authenticated caller.
 *
 * @param requestId value of {@code X-Request-Id} — the ONLY tracking id
 *                  (API Standard §1); received or generated, echoed on the
 *                  response and in {@code meta.requestId}
 * @param userId    value of {@code X-User-Id} when a user is acting; recorded in
 *                  logs only — the audit actor stays the authenticated caller
 */
public record RequestContextData(String requestId, String userId, String clientIp, Instant receivedAt) {
}
