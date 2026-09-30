package com.apargo.platform.contract.access;

import com.apargo.platform.contract.event.EventIds;
import com.apargo.platform.contract.event.EventSchemaVersion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An access event (sign-in, sign-out, token refresh, access denied). Produced
 * only by the auth service and the gateway; business services publish
 * {@code AuditEventDto} instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"eventId", "schemaVersion", "sourceService", "environment", "eventType", "status",
        "orgId", "actor", "reason", "metadata", "requestId", "traceId", "ip", "userAgent", "occurredAt"})
public record AccessEventDto(
        String eventId,
        Integer schemaVersion,
        String sourceService,
        String environment,
        String eventType,
        AccessEventStatus status,
        Long orgId,
        AccessActorDto actor,
        String reason,
        Map<String, Object> metadata,
        String requestId,
        String traceId,
        String ip,
        String userAgent,
        Instant occurredAt) {

    public AccessEventDto {
        metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /** A new event with {@code eventId}, {@code schemaVersion} and {@code occurredAt} set. */
    public static AccessEventDto of(String sourceService, String environment, String eventType,
                                    AccessEventStatus status, Long orgId, AccessActorDto actor,
                                    String reason, Map<String, Object> metadata, String requestId,
                                    String traceId, String ip, String userAgent) {
        return new AccessEventDto(EventIds.newId(), EventSchemaVersion.CURRENT, sourceService, environment,
                eventType, status, orgId, actor, reason, metadata, requestId, traceId, ip, userAgent,
                Instant.now());
    }
}
