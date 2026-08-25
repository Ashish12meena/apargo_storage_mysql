package com.aigreentick.services.storage.domain.event;

import java.time.Instant;

/**
 * Something that happened, written to the outbox in the SAME transaction as the
 * state change that produced it (ADR-006).
 *
 * <p>Payloads carry IDENTIFIERS only — never file bytes, presigned URLs, or
 * credentials. A URL in a payload is a capability sitting in a table that outlives
 * its own expiry semantics.
 */
public sealed interface DomainEvent {

    String AGGREGATE_MEDIA = "media";
    String AGGREGATE_QUOTA = "quota";
    String AGGREGATE_UPLOAD = "upload";

    String eventType();

    String aggregateType();

    String aggregateId();

    Instant occurredAt();

    record MediaCreated(String aggregateId, long orgId, long projectId, String storageKey,
                        long sizeBytes, String mediaType, String contentType,
                        Instant occurredAt) implements DomainEvent {
        public String eventType() { return "media.created"; }
        public String aggregateType() { return AGGREGATE_MEDIA; }
    }

    record MediaDeleted(String aggregateId, long orgId, long projectId, String storageKey,
                        long sizeBytes, boolean permanent, Instant occurredAt) implements DomainEvent {
        public String eventType() { return "media.deleted"; }
        public String aggregateType() { return AGGREGATE_MEDIA; }
    }

    record MediaPurged(String aggregateId, long orgId, long projectId,
                       Instant occurredAt) implements DomainEvent {
        public String eventType() { return "media.purged"; }
        public String aggregateType() { return AGGREGATE_MEDIA; }
    }

    record MediaRestored(String aggregateId, long orgId, long projectId,
                         Instant occurredAt) implements DomainEvent {
        public String eventType() { return "media.restored"; }
        public String aggregateType() { return AGGREGATE_MEDIA; }
    }

    record QuotaThresholdCrossed(String aggregateId, long orgId, Long projectId, int percent,
                                 long usedBytes, long maxBytes, Instant occurredAt) implements DomainEvent {
        public String eventType() { return "quota.threshold.crossed"; }
        public String aggregateType() { return AGGREGATE_QUOTA; }
    }

    /**
     * Requests removal of every file for a tenant.
     *
     * <p>Carries a continuation cursor: teardown of a large tenant is processed in
     * bounded batches, each re-appending this event until no rows remain. That
     * reuses the outbox retry and dead-letter machinery instead of inventing a
     * second job system, and it means a crash mid-teardown simply resumes.
     *
     * @param filesRemoved running total of rows ACTUALLY marked so far. Carried
     *                     through the continuation rather than derived from
     *                     {@code batchesDone × batchSize}, which overcounts: the
     *                     final batch is nearly always partial.
     */
    record TenantTeardownRequested(String aggregateId, long orgId, Long projectId,
                                   boolean permanent, String requestedBy, int batchesDone,
                                   long filesRemoved, Instant occurredAt) implements DomainEvent {
        public String eventType() { return "tenant.teardown.requested"; }
        public String aggregateType() { return AGGREGATE_MEDIA; }
    }

    record TenantTeardownCompleted(String aggregateId, long orgId, Long projectId,
                                   long filesRemoved, Instant occurredAt) implements DomainEvent {
        public String eventType() { return "tenant.teardown.completed"; }
        public String aggregateType() { return AGGREGATE_MEDIA; }
    }

    record UploadSessionExpired(String aggregateId, long orgId, long projectId, String storageKey,
                               long reclaimedBytes, Instant occurredAt) implements DomainEvent {
        public String eventType() { return "upload.session.expired"; }
        public String aggregateType() { return AGGREGATE_UPLOAD; }
    }
}
