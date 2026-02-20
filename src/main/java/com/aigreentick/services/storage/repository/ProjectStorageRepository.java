package com.aigreentick.services.storage.repository;

import com.aigreentick.services.storage.domain.ProjectStorage;
import com.aigreentick.services.storage.domain.ProjectStorageId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectStorageRepository extends JpaRepository<ProjectStorage, ProjectStorageId> {

    /**
     * Pessimistic write lock — always acquired BEFORE the org-level lock
     * to maintain consistent lock ordering and prevent deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProjectStorage p WHERE p.orgId = :orgId AND p.projectId = :projectId")
    Optional<ProjectStorage> findByIdForUpdate(@Param("orgId") Long orgId,
                                                @Param("projectId") Long projectId);

    List<ProjectStorage> findByOrgId(Long orgId);

    /**
     * Actual media bytes for a specific project — used during reconciliation.
     */
    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM Media m " +
           "WHERE m.organisationId = :orgId AND m.projectId = :projectId AND m.status = 'ACTIVE'")
    long sumActiveMediaBytes(@Param("orgId") Long orgId,
                             @Param("projectId") Long projectId);
}