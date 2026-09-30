package com.apargo.platform.contract.audit;

import com.apargo.platform.contract.event.EventIds;
import com.apargo.platform.contract.event.EventSchemaVersion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One audit event: a business change, or a business failure after a request was
 * accepted. Serialized as camelCase JSON, timestamps as UTC ISO-8601, absent
 * optional fields omitted.
 *
 * <p>Build with {@link #builder()}: it sets {@code eventId} (UUIDv7),
 * {@code schemaVersion} and {@code occurredAt}. The event id is kept for every
 * delivery attempt so consumers can de-duplicate.
 *
 * <p>Check with {@link AuditEventValidator#validate(AuditEventDto)} before sending.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"eventId", "schemaVersion", "sourceService", "environment", "module", "eventType",
        "status", "orgId", "projectId", "actor", "entity", "changes", "metadata", "error", "channel",
        "requestId", "traceId", "ip", "userAgent", "occurredAt"})
public record AuditEventDto(
        String eventId,
        Integer schemaVersion,
        String sourceService,
        String environment,
        String module,
        String eventType,
        AuditEventStatus status,
        Long orgId,
        Long projectId,
        AuditActorDto actor,
        AuditEntityDto entity,
        List<AuditChangeDto> changes,
        Map<String, Object> metadata,
        AuditErrorDto error,
        AuditChannel channel,
        String requestId,
        String traceId,
        String ip,
        String userAgent,
        Instant occurredAt) {

    public AuditEventDto {
        changes = changes == null ? null : List.copyOf(changes);
        metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder; not thread-safe. */
    public static final class Builder {

        private String eventId = EventIds.newId();
        private Integer schemaVersion = EventSchemaVersion.CURRENT;
        private String sourceService;
        private String environment;
        private String module;
        private String eventType;
        private AuditEventStatus status;
        private Long orgId;
        private Long projectId;
        private AuditActorDto actor;
        private AuditEntityDto entity;
        private List<AuditChangeDto> changes;
        private Map<String, Object> metadata;
        private AuditErrorDto error;
        private AuditChannel channel;
        private String requestId;
        private String traceId;
        private String ip;
        private String userAgent;
        private Instant occurredAt = Instant.now();

        private Builder() {
        }

        /** Overrides the generated id; use only to rebuild an event being replayed. */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder status(AuditEventStatus status) {
            this.status = status;
            return this;
        }

        public Builder orgId(Long orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder actor(AuditActorDto actor) {
            this.actor = actor;
            return this;
        }

        public Builder entity(AuditEntityDto entity) {
            this.entity = entity;
            return this;
        }

        /** Replaces the change list. An empty list is written as {@code []}. */
        public Builder changes(List<AuditChangeDto> changes) {
            this.changes = changes == null ? null : new ArrayList<>(changes);
            return this;
        }

        /** Appends one change, creating the list if needed. */
        public Builder change(String field, Object oldValue, Object newValue) {
            if (this.changes == null) {
                this.changes = new ArrayList<>();
            }
            this.changes.add(new AuditChangeDto(field, oldValue, newValue));
            return this;
        }

        /** Adds one metadata entry; a null key or value is ignored. */
        public Builder metadata(String key, Object value) {
            if (key == null || value == null) {
                return this;
            }
            if (this.metadata == null) {
                this.metadata = new LinkedHashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /** Adds every non-null entry. */
        public Builder metadata(Map<String, ?> entries) {
            if (entries != null) {
                entries.forEach(this::metadata);
            }
            return this;
        }

        public Builder error(AuditErrorDto error) {
            this.error = error;
            return this;
        }

        public Builder channel(AuditChannel channel) {
            this.channel = channel;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /** Overrides the build-time timestamp, e.g. with the time the change was committed. */
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public AuditEventDto build() {
            return new AuditEventDto(eventId, schemaVersion, sourceService, environment, module, eventType,
                    status, orgId, projectId, actor, entity, changes, metadata, error, channel,
                    requestId, traceId, ip, userAgent, occurredAt);
        }
    }
}
