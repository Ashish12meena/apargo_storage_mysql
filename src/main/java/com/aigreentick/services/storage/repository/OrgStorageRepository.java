package com.aigreentick.services.storage.repository;

import com.aigreentick.services.storage.domain.OrgStorage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrgStorageRepository extends JpaRepository<OrgStorage, Long> {

    /**
     * Pessimistic write lock — kept for release/reconciliation paths.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrgStorage o WHERE o.orgId = :orgId")
    Optional<OrgStorage> findByIdForUpdate(@Param("orgId") Long orgId);

    /**
     * Plain read for optimistic locking flow.
     */
    @Query("SELECT o FROM OrgStorage o WHERE o.orgId = :orgId")
    Optional<OrgStorage> findByOrgId(@Param("orgId") Long orgId);

    @Query("SELECT COALESCE(SUM(p.usedBytes), 0) FROM ProjectStorage p WHERE p.orgId = :orgId")
    long sumProjectUsedBytes(@Param("orgId") Long orgId);

    /**
     * Atomic quota reservation at org level.
     * Returns 1 if reserved, 0 if quota exceeded or row not found.
     */
    @Modifying
    @Query("UPDATE OrgStorage o " +
            "SET o.usedBytes = o.usedBytes + :size, o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.orgId = :orgId " +
            "AND o.usedBytes + :size <= o.maxBytes")
    int incrementUsage(@Param("orgId") Long orgId, @Param("size") long size);

    /**
     * Atomic quota release at org level.
     */
    @Modifying
    @Query("UPDATE OrgStorage o " +
            "SET o.usedBytes = CASE WHEN o.usedBytes >= :size THEN o.usedBytes - :size ELSE 0 END, " +
            "    o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.orgId = :orgId")
    int decrementUsage(@Param("orgId") Long orgId, @Param("size") long size);
}