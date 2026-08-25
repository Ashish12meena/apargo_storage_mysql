package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Claims a batch with {@code FOR UPDATE SKIP LOCKED}, so every replica can poll
     * concurrently and each takes a disjoint set — no leader election, no partition
     * assignment.
     *
     * <p>Native because JPQL has no SKIP LOCKED. Hibernate can express it through a
     * lock-timeout hint, but the SQL states the intent plainly and this is the one
     * query where the locking behaviour IS the design.
     */
    @Query(value = """
            SELECT * FROM outbox_event
             WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now)
             ORDER BY id
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> claimBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = 'IN_FLIGHT' WHERE e.id IN :ids")
    int markInFlight(@Param("ids") List<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = 'DISPATCHED', e.dispatchedAt = :now, "
            + "e.lastError = NULL WHERE e.id = :id")
    int markDispatched(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = :status, e.attempts = e.attempts + 1, "
            + "e.nextRetryAt = :nextRetryAt, e.lastError = :reason WHERE e.id = :id")
    int markFailed(@Param("id") Long id, @Param("status") String status,
                   @Param("nextRetryAt") Instant nextRetryAt, @Param("reason") String reason);

    /** Dispatched rows are deleted, or the outbox becomes the largest table here. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = 'DISPATCHED' AND e.dispatchedAt < :cutoff")
    int deleteDispatchedBefore(@Param("cutoff") Instant cutoff);

    @Query(value = """
            SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(6)), 0)
              FROM outbox_event WHERE status IN ('PENDING', 'IN_FLIGHT')
            """, nativeQuery = true)
    long oldestPendingAgeSeconds();

    /** Recovers rows stuck IN_FLIGHT because a replica died mid-dispatch. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = 'PENDING' "
            + "WHERE e.status = 'IN_FLIGHT' AND e.createdAt < :cutoff")
    int requeueStale(@Param("cutoff") Instant cutoff);
}
