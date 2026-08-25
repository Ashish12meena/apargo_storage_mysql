package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.ProjectStorageEntity;
import com.aigreentick.services.storage.infrastructure.persistence.entity.ProjectStorageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** @see OrgStorageJpaRepository for why these are conditional bulk updates */
interface ProjectStorageJpaRepository
        extends JpaRepository<ProjectStorageEntity, ProjectStorageId> {

    Optional<ProjectStorageEntity> findByOrgIdAndProjectId(Long orgId, Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectStorageEntity p
               SET p.usedBytes = p.usedBytes + :size, p.version = p.version + 1
             WHERE p.orgId = :orgId AND p.projectId = :projectId
               AND p.usedBytes + :size <= p.maxBytes
            """)
    int reserve(@Param("orgId") Long orgId, @Param("projectId") Long projectId,
                @Param("size") long size);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectStorageEntity p
               SET p.usedBytes = CASE WHEN p.usedBytes >= :size THEN p.usedBytes - :size ELSE 0 END,
                   p.version = p.version + 1
             WHERE p.orgId = :orgId AND p.projectId = :projectId
            """)
    int release(@Param("orgId") Long orgId, @Param("projectId") Long projectId,
                @Param("size") long size);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectStorageEntity p
               SET p.usedBytes = :actual, p.version = p.version + 1
             WHERE p.orgId = :orgId AND p.projectId = :projectId
            """)
    int correctUsage(@Param("orgId") Long orgId, @Param("projectId") Long projectId,
                     @Param("actual") long actual);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO project_storage
                (org_id, project_id, max_bytes, used_bytes, version, created_at, updated_at)
            VALUES (:orgId, :projectId, :max, 0, 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE max_bytes = :max, updated_at = NOW(6), version = version + 1
            """, nativeQuery = true)
    int upsert(@Param("orgId") Long orgId, @Param("projectId") Long projectId,
               @Param("max") long max);

    @Query("SELECT COALESCE(MAX(p.maxBytes), 0) FROM ProjectStorageEntity p WHERE p.orgId = :orgId")
    long maxProjectLimit(@Param("orgId") Long orgId);

    @Query("SELECT COALESCE(SUM(p.maxBytes), 0) FROM ProjectStorageEntity p "
            + "WHERE p.orgId = :orgId AND p.projectId <> :projectId")
    long sumLimitsExcluding(@Param("orgId") Long orgId, @Param("projectId") Long projectId);
}
