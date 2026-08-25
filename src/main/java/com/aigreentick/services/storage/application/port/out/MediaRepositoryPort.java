package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.application.shared.MediaListQuery;
import com.aigreentick.services.storage.application.shared.PageView;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Media persistence.
 *
 * <p>CRITICAL CONSTRAINT: there is no tenant-blind lookup on this interface. No
 * {@code findById(MediaId)}, no {@code deleteById(MediaId)}. The predecessor's
 * {@code deleteById(Long)} took no tenant scope, so wiring it to a controller was
 * a direct IDOR against an auto-increment id. Removing the method makes that class
 * of bug unwritable rather than merely discouraged.
 *
 * <p>The {@code *ForMaintenance} methods are the deliberate exception, used only by
 * schedulers running as {@code Actor.SYSTEM}. They are named to be conspicuous in
 * review and are covered by an ArchUnit rule restricting their callers.
 */
public interface MediaRepositoryPort {

    Media save(Media media);

    Optional<Media> findByIdForTenant(MediaId id, TenantRef tenant);

    Optional<Media> findByStorageKeyForTenant(StorageKey key, TenantRef tenant);

    PageView<Media> search(MediaListQuery query);

    /**
     * Conditional state change. Returns rows affected, so a concurrent duplicate
     * delete observes 0 and skips the quota release instead of double-releasing.
     */
    int transitionStatus(MediaId id, TenantRef tenant, MediaStatus from, MediaStatus to,
                         Long actorUserId, Instant at, Instant purgeAfter);

    // ── Maintenance (SYSTEM actor only) ─────────────────────────────────────

    Optional<Media> findByIdForMaintenance(MediaId id);

    /** Rows whose purge window has opened. Driven by purge_after, not deleted_at. */
    List<Media> findPurgeableForMaintenance(Instant now, int limit);

    /**
     * Bulk soft-delete for tenant teardown. Bounded by {@code limit} so one pass
     * cannot lock an unbounded number of rows.
     *
     * @param projectId null tears down every project in the org
     * @return rows affected; zero means the teardown is complete
     */
    int softDeleteTenantBatchForMaintenance(long orgId, Long projectId, Long actorUserId,
                                            Instant at, Instant purgeAfter, int limit);

    /** Remaining live rows for a tenant. Drives the teardown continuation check. */
    long countLiveForMaintenance(long orgId, Long projectId);

    /** Project ids with media under an org. Used to recompute quota after teardown. */
    List<TenantRef> findTenantsForOrgForMaintenance(long orgId);

    List<Media> findByStatusForMaintenance(MediaStatus status, Instant olderThan, int limit);

    int markPurgedForMaintenance(MediaId id, Instant at);

    long sumActiveBytesForMaintenance(TenantRef tenant);

    List<TenantRef> findDistinctTenantsForMaintenance();

    boolean existsByStorageKeyForMaintenance(StorageKey key);
}
