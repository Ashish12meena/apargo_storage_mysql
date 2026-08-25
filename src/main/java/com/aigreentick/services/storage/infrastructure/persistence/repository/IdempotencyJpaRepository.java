package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.aigreentick.services.storage.infrastructure.persistence.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface IdempotencyJpaRepository
        extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM IdempotencyRecordEntity r WHERE r.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
