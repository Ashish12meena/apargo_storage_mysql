package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.SchedulerLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface SchedulerLockJpaRepository extends JpaRepository<SchedulerLockEntity, String> {

    /**
     * Takes the lock when free OR when a previous lease has expired, so an
     * ungracefully terminated pod cannot hold it forever.
     *
     * <p>Native: the whole point is the atomic insert-or-conditionally-update, and
     * a read-then-write would let two replicas both believe they won.
     *
     * <p>MySQL returns 1 for an insert and 2 for an applied update; 0 means the IF
     * conditions preserved the existing values, i.e. a live lease.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO scheduler_lock (lock_name, locked_by, locked_at, expires_at)
            VALUES (:name, :owner, :now, :expiresAt)
            ON DUPLICATE KEY UPDATE
                locked_by  = IF(expires_at < :now, VALUES(locked_by),  locked_by),
                locked_at  = IF(expires_at < :now, VALUES(locked_at),  locked_at),
                expires_at = IF(expires_at < :now, VALUES(expires_at), expires_at)
            """, nativeQuery = true)
    int tryAcquire(@Param("name") String name, @Param("owner") String owner,
                   @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SchedulerLockEntity l WHERE l.lockName = :name AND l.lockedBy = :owner")
    int releaseOwned(@Param("name") String name, @Param("owner") String owner);
}
