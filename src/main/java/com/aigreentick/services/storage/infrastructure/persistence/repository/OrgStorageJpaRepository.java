package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.OrgStorageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Quota arithmetic as conditional bulk updates.
 *
 * <p>{@code @Modifying} statements bypass the persistence context and go straight
 * to SQL, which is exactly what is needed: the invariant {@code used <= max} is
 * enforced by the WHERE clause in the database engine, so it holds even if
 * application code is wrong. Rows-affected tells the caller whether it won.
 *
 * <p>{@code clearAutomatically} and {@code flushAutomatically} are mandatory on
 * every one of these. Without them a previously-loaded entity stays cached with a
 * stale {@code usedBytes} after the update — the sharp edge of bulk operations
 * inside JPA.
 */
interface OrgStorageJpaRepository extends JpaRepository<OrgStorageEntity, Long> {

    /** Atomic reserve. Returns 0 when the org has insufficient quota. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrgStorageEntity o
               SET o.usedBytes = o.usedBytes + :size, o.version = o.version + 1
             WHERE o.orgId = :orgId
               AND o.usedBytes + :size <= o.maxBytes
            """)
    int reserve(@Param("orgId") Long orgId, @Param("size") long size);

    /** Release floors at zero rather than going negative. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrgStorageEntity o
               SET o.usedBytes = CASE WHEN o.usedBytes >= :size THEN o.usedBytes - :size ELSE 0 END,
                   o.version = o.version + 1
             WHERE o.orgId = :orgId
            """)
    int release(@Param("orgId") Long orgId, @Param("size") long size);

    /** Org usage is the sum of its projects; recompute rather than guess. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE org_storage o
               SET o.used_bytes = (SELECT COALESCE(SUM(p.used_bytes), 0)
                                     FROM project_storage p WHERE p.org_id = o.org_id),
                   o.version = o.version + 1
             WHERE o.org_id = :orgId
            """, nativeQuery = true)
    int recomputeUsageFromProjects(@Param("orgId") Long orgId);

    /**
     * Idempotent upsert. Native because {@code ON DUPLICATE KEY UPDATE} has no JPQL
     * equivalent, and an entity-managed read-then-write would race two concurrent
     * provisioning calls.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO org_storage (org_id, max_bytes, used_bytes, version, created_at, updated_at)
            VALUES (:orgId, :max, 0, 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE max_bytes = :max, updated_at = NOW(6), version = version + 1
            """, nativeQuery = true)
    int upsert(@Param("orgId") Long orgId, @Param("max") long max);
}
