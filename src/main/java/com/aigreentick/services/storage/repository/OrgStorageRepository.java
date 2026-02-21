package com.aigreentick.services.storage.repository;

import com.aigreentick.services.storage.domain.OrgStorage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}