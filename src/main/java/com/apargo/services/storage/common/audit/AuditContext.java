package com.apargo.services.storage.common.audit;

import com.apargo.platform.contract.audit.AuditActorDto;
import com.apargo.platform.contract.audit.AuditChannel;

/**
 * Ambient facts every audit event carries, resolved once per event by
 * {@link AuditContextProvider}.
 *
 * @param requestId {@code X-Request-Id}; null for scheduled jobs
 * @param traceId   backend trace id; always present
 * @param ip        client address; null until the gateway provides a trusted one
 * @param userAgent client user agent; null until the gateway provides a trusted one
 */
public record AuditContext(AuditActorDto actor, AuditChannel channel, String requestId, String traceId,
                           String ip, String userAgent) {
}
