package com.aigreentick.services.storage.repository;

import com.aigreentick.services.storage.domain.ProjectStorage;
import com.aigreentick.services.storage.domain.ProjectStorageId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectStorageRepository extends JpaRepository<ProjectStorage, ProjectStorageId> {

       /**
        * Pessimistic write lock — kept for release/reconciliation paths.
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT p FROM ProjectStorage p WHERE p.orgId = :orgId AND p.projectId = :projectId")
       Optional<ProjectStorage> findByIdForUpdate(@Param("orgId") Long orgId,
                     @Param("projectId") Long projectId);

       /**
        * Plain read for optimistic locking flow.
        */
       @Query("SELECT p FROM ProjectStorage p WHERE p.orgId = :orgId AND p.projectId = :projectId")
       Optional<ProjectStorage> findByOrgAndProject(@Param("orgId") Long orgId,
                     @Param("projectId") Long projectId);

       List<ProjectStorage> findByOrgId(Long orgId);

       @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM Media m " +
                     "WHERE m.organisationId = :orgId AND m.projectId = :projectId AND m.status = 'ACTIVE'")
       long sumActiveMediaBytes(@Param("orgId") Long orgId,
                     @Param("projectId") Long projectId);

       /**
        * Atomic quota reservation — single UPDATE with capacity check in WHERE.
        * Returns 1 if reserved, 0 if quota exceeded or row not found.
        */
       @Modifying
       @Query("UPDATE ProjectStorage p " +
                     "SET p.usedBytes = p.usedBytes + :size, p.updatedAt = CURRENT_TIMESTAMP " +
                     "WHERE p.orgId = :orgId AND p.projectId = :projectId " +
                     "AND p.usedBytes + :size <= p.maxBytes")
       int incrementUsage(@Param("orgId") Long orgId,
                     @Param("projectId") Long projectId,
                     @Param("size") long size);

       /**
        * Atomic quota release.
        */
       @Modifying
       @Query("UPDATE ProjectStorage p " +
                     "SET p.usedBytes = CASE WHEN p.usedBytes >= :size THEN p.usedBytes - :size ELSE 0 END, " +
                     "    p.updatedAt = CURRENT_TIMESTAMP " +
                     "WHERE p.orgId = :orgId AND p.projectId = :projectId")
       int decrementUsage(@Param("orgId") Long orgId,
                     @Param("projectId") Long projectId,
                     @Param("size") long size);
}