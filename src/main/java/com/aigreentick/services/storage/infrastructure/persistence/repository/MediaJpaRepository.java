package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface. NEVER injected outside this package: doing so reinstates
 * the tenant-blind {@code findById(id)} that {@code MediaRepositoryPort}
 * deliberately omits.
 */
interface MediaJpaRepository extends JpaRepository<MediaEntity, Long> {

    Optional<MediaEntity> findByIdAndOrganisationIdAndProjectId(Long id, Long orgId, Long projectId);

    Optional<MediaEntity> findByStorageKeyAndOrganisationIdAndProjectId(String key, Long orgId, Long projectId);

    boolean existsByStorageKey(String storageKey);

    /**
     * Conditional transition. Rows affected tells the caller whether it won a
     * concurrent race, so quota is never released twice.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MediaEntity m
               SET m.status = :toStatus,
                   m.updatedAt = :at,
                   m.deletedAt = CASE WHEN :toStatus = 'DELETED' THEN :at
                                      WHEN :toStatus = 'ACTIVE'  THEN NULL
                                      ELSE m.deletedAt END,
                   m.deletedBy = CASE WHEN :toStatus = 'DELETED' THEN :actorId
                                      WHEN :toStatus = 'ACTIVE'  THEN NULL
                                      ELSE m.deletedBy END,
                   m.purgeAfter = CASE WHEN :toStatus = 'DELETED' THEN :purgeAfter
                                       WHEN :toStatus = 'ACTIVE'  THEN NULL
                                       ELSE m.purgeAfter END
             WHERE m.id = :id
               AND m.organisationId = :orgId
               AND m.projectId = :projectId
               AND m.status = :fromStatus
            """)
    int transitionStatus(@Param("id") Long id, @Param("orgId") Long orgId,
                         @Param("projectId") Long projectId, @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus, @Param("actorId") Long actorId,
                         @Param("at") Instant at, @Param("purgeAfter") Instant purgeAfter);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaEntity m SET m.status = 'PURGED', m.updatedAt = :at "
            + "WHERE m.id = :id AND m.status = 'DELETED'")
    int markPurged(@Param("id") Long id, @Param("at") Instant at);

    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM MediaEntity m "
            + "WHERE m.organisationId = :orgId AND m.projectId = :projectId "
            + "AND m.status IN ('ACTIVE', 'PENDING', 'QUARANTINED')")
    long sumBillableBytes(@Param("orgId") Long orgId, @Param("projectId") Long projectId);

    @Query("SELECT m FROM MediaEntity m WHERE m.status = 'DELETED' "
            + "AND (m.purgeAfter IS NULL OR m.purgeAfter <= :now) ORDER BY m.purgeAfter ASC")
    List<MediaEntity> findPurgeable(@Param("now") Instant now,
                                    org.springframework.data.domain.Pageable pageable);

    /**
     * Bulk soft-delete for teardown. The subselect bounds the batch: JPQL has no
     * LIMIT on UPDATE, and an unbounded update would lock every row for a large
     * tenant.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE media
               SET status = 'DELETED', updated_at = :at, deleted_at = :at,
                   deleted_by = :actorId, purge_after = :purgeAfter
             WHERE organisation_id = :orgId
               AND (:projectId IS NULL OR project_id = :projectId)
               AND status IN ('ACTIVE', 'PENDING', 'QUARANTINED')
             LIMIT :batchSize
            """, nativeQuery = true)
    int softDeleteTenantBatch(@Param("orgId") Long orgId, @Param("projectId") Long projectId,
                              @Param("actorId") Long actorId, @Param("at") Instant at,
                              @Param("purgeAfter") Instant purgeAfter,
                              @Param("batchSize") int batchSize);

    @Query("SELECT COUNT(m) FROM MediaEntity m WHERE m.organisationId = :orgId "
            + "AND (:projectId IS NULL OR m.projectId = :projectId) "
            + "AND m.status IN ('ACTIVE', 'PENDING', 'QUARANTINED')")
    long countLive(@Param("orgId") Long orgId, @Param("projectId") Long projectId);

    @Query("SELECT DISTINCT m.organisationId, m.projectId FROM MediaEntity m "
            + "WHERE m.organisationId = :orgId")
    List<Object[]> findTenantsForOrg(@Param("orgId") Long orgId);

    @Query("SELECT m FROM MediaEntity m WHERE m.status = :status AND m.createdAt < :cutoff "
            + "ORDER BY m.createdAt ASC")
    List<MediaEntity> findByStatusOlderThan(@Param("status") String status,
                                            @Param("cutoff") Instant cutoff,
                                            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT DISTINCT m.organisationId, m.projectId FROM MediaEntity m")
    List<Object[]> findDistinctTenants();
}
