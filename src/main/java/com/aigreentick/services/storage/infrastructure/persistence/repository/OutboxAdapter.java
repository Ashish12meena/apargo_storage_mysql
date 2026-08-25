package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implements {@link OutboxPort}.
 *
 * <p>{@code append} must be called inside the same transaction as the state change
 * that produced the event. That atomicity is why a table is used instead of
 * publishing straight to a broker (ADR-006).
 */
@Repository
public class OutboxAdapter implements OutboxPort {

    private final OutboxJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public OutboxAdapter(OutboxJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent event) {
        Instant now = Instant.now();
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setAggregateType(event.aggregateType());
        entity.setAggregateId(event.aggregateId());
        entity.setEventType(event.eventType());
        entity.setPayload(serialise(event));
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setNextRetryAt(now);
        entity.setCreatedAt(now);
        jpa.save(entity);
    }

    private String serialise(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Failing here rolls back the state change too, which is correct: an
            // unserialisable event means the side effect would silently never happen.
            throw new IllegalStateException("cannot serialise event " + event.eventType(), e);
        }
    }

    @Override
    @Transactional
    public List<OutboxRecord> claimBatch(int limit) {
        List<OutboxEventEntity> claimed = jpa.claimBatch(Instant.now(), limit);
        if (claimed.isEmpty()) {
            return List.of();
        }
        // Marked IN_FLIGHT inside the same transaction that holds the row locks, so
        // a crash before dispatch leaves them reclaimable by the stale sweep.
        jpa.markInFlight(claimed.stream().map(OutboxEventEntity::getId).toList());

        return claimed.stream()
                .map(e -> new OutboxRecord(e.getId(), e.getAggregateType(), e.getAggregateId(),
                        e.getEventType(), e.getPayload(), e.getAttempts(), e.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void markDispatched(long recordId) {
        jpa.markDispatched(recordId, Instant.now());
    }

    @Override
    @Transactional
    public void markFailed(long recordId, String reason, Instant nextRetryAt, boolean deadLetter) {
        jpa.markFailed(recordId, deadLetter ? "FAILED" : "PENDING", nextRetryAt, truncate(reason));
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    @Override
    @Transactional
    public int deleteDispatchedBefore(Instant cutoff) {
        return jpa.deleteDispatchedBefore(cutoff);
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestPendingAgeSeconds() {
        return jpa.oldestPendingAgeSeconds();
    }

    @Transactional
    public int requeueStale(Instant staleBefore) {
        return jpa.requeueStale(staleBefore);
    }
}
