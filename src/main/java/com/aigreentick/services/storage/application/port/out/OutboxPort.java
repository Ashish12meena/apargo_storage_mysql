package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox.
 *
 * <p>{@link #append} MUST be called inside the same transaction as the state
 * change that produced the event. That atomicity is why a table is used instead of
 * publishing directly to a broker: a direct publish cannot be made atomic with a
 * database commit without two-phase commit or a loss window (ADR-006).
 */
public interface OutboxPort {

    void append(DomainEvent event);

    /**
     * Claims a batch using {@code FOR UPDATE SKIP LOCKED}, so N replicas share the
     * work with no coordination between them.
     */
    List<OutboxRecord> claimBatch(int limit);

    void markDispatched(long recordId);

    /** Increments attempts and schedules a backed-off retry; DLQs past the limit. */
    void markFailed(long recordId, String reason, Instant nextRetryAt, boolean deadLetter);

    int deleteDispatchedBefore(Instant cutoff);

    long oldestPendingAgeSeconds();

    record OutboxRecord(long id, String aggregateType, String aggregateId, String eventType,
                        String payloadJson, int attempts, Instant createdAt) {
    }
}
