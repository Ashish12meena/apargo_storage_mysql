package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.shared.MediaListQuery;
import com.aigreentick.services.storage.application.shared.PageView;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.infrastructure.persistence.entity.MediaEntity;
import com.aigreentick.services.storage.infrastructure.persistence.mapper.MediaEntityMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implements {@link MediaRepositoryPort}. */
@Repository
public class MediaRepositoryAdapter implements MediaRepositoryPort {

    private final MediaJpaRepository jpa;
    private final MediaKeysetQuery keysetQuery;
    private final MediaEntityMapper mapper;
    private final StorageProperties storageProperties;

    public MediaRepositoryAdapter(MediaJpaRepository jpa, MediaKeysetQuery keysetQuery,
                                  MediaEntityMapper mapper, StorageProperties storageProperties) {
        this.jpa = jpa;
        this.keysetQuery = keysetQuery;
        this.mapper = mapper;
        this.storageProperties = storageProperties;
    }

    @Override
    public Media save(Media media) {
        MediaEntity entity = media.id() == null
                ? newEntity()
                : jpa.findById(media.id().value()).orElseGet(this::newEntity);
        mapper.apply(media, entity, storageProperties.activeProvider().toUpperCase());
        MediaEntity saved = jpa.save(entity);
        if (media.id() == null) {
            media.assignId(MediaId.of(saved.getId()));
        }
        return mapper.toDomain(saved);
    }

    /**
     * {@code MediaEntity}'s no-arg constructor is protected — the entity is not
     * meant to be instantiated outside persistence, and this adapter is that inside.
     */
    private MediaEntity newEntity() {
        try {
            Constructor<MediaEntity> ctor = MediaEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate MediaEntity", e);
        }
    }

    @Override
    public Optional<Media> findByIdForTenant(MediaId id, TenantRef tenant) {
        if (id == null) {
            return Optional.empty();
        }
        return jpa.findByIdAndOrganisationIdAndProjectId(id.value(), tenant.orgId(), tenant.projectId())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Media> findByStorageKeyForTenant(StorageKey key, TenantRef tenant) {
        return jpa.findByStorageKeyAndOrganisationIdAndProjectId(
                key.value(), tenant.orgId(), tenant.projectId()).map(mapper::toDomain);
    }

    @Override
    public PageView<Media> search(MediaListQuery query) {
        List<MediaEntity> rows = keysetQuery.fetchPage(query);
        boolean hasMore = rows.size() > query.limit();
        List<MediaEntity> page = hasMore ? rows.subList(0, query.limit()) : rows;

        List<Media> items = new ArrayList<>(page.size());
        for (MediaEntity row : page) {
            items.add(mapper.toDomain(row));
        }
        String nextCursor = hasMore && !page.isEmpty()
                ? keysetQuery.nextCursor(page.get(page.size() - 1))
                : null;
        return new PageView<>(items, nextCursor, hasMore);
    }

    @Override
    public int transitionStatus(MediaId id, TenantRef tenant, MediaStatus from, MediaStatus to,
                                Long actorUserId, Instant at, Instant purgeAfter) {
        return jpa.transitionStatus(id.value(), tenant.orgId(), tenant.projectId(),
                from.name(), to.name(), actorUserId, at, purgeAfter);
    }

    // ── Maintenance ─────────────────────────────────────────────────────────

    @Override
    public Optional<Media> findByIdForMaintenance(MediaId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Media> findPurgeableForMaintenance(Instant now, int limit) {
        return jpa.findPurgeable(now, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public int softDeleteTenantBatchForMaintenance(long orgId, Long projectId, Long actorUserId,
                                                   Instant at, Instant purgeAfter, int limit) {
        return jpa.softDeleteTenantBatch(orgId, projectId, actorUserId, at, purgeAfter, limit);
    }

    @Override
    public long countLiveForMaintenance(long orgId, Long projectId) {
        return jpa.countLive(orgId, projectId);
    }

    @Override
    public List<TenantRef> findTenantsForOrgForMaintenance(long orgId) {
        return jpa.findTenantsForOrg(orgId).stream()
                .map(row -> new TenantRef(((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    @Override
    public List<Media> findByStatusForMaintenance(MediaStatus status, Instant olderThan, int limit) {
        return jpa.findByStatusOlderThan(status.name(), olderThan, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public int markPurgedForMaintenance(MediaId id, Instant at) {
        return jpa.markPurged(id.value(), at);
    }

    @Override
    public long sumActiveBytesForMaintenance(TenantRef tenant) {
        return jpa.sumBillableBytes(tenant.orgId(), tenant.projectId());
    }

    @Override
    public List<TenantRef> findDistinctTenantsForMaintenance() {
        return jpa.findDistinctTenants().stream()
                .map(row -> new TenantRef(((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    @Override
    public boolean existsByStorageKeyForMaintenance(StorageKey key) {
        return jpa.existsByStorageKey(key.value());
    }
}
