package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.shared.Cursor;
import com.aigreentick.services.storage.application.shared.MediaListQuery;
import com.aigreentick.services.storage.infrastructure.persistence.entity.MediaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Keyset pagination over {@code idx_media_keyset}.
 *
 * <p>Written by hand rather than through Spring Data because the predicate is a
 * row-value comparison — {@code (created_at, id) < (?, ?)} — expressed here as its
 * expanded equivalent so it stays portable and index-friendly. Offset pagination
 * would degrade linearly with page depth.
 */
@Component
public class MediaKeysetQuery {

    private final EntityManager entityManager;

    public MediaKeysetQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<MediaEntity> fetchPage(MediaListQuery query) {
        Cursor cursor = Cursor.decode(query.cursor());

        StringBuilder jpql = new StringBuilder(
                "SELECT m FROM MediaEntity m WHERE m.organisationId = :orgId "
                        + "AND m.projectId = :projectId AND m.status = 'ACTIVE'");
        if (query.mediaType() != null) {
            jpql.append(" AND m.mediaType = :mediaType");
        }
        if (cursor != null) {
            jpql.append(" AND (m.createdAt < :cursorTime "
                    + "OR (m.createdAt = :cursorTime AND m.id < :cursorId))");
        }
        jpql.append(" ORDER BY m.createdAt DESC, m.id DESC");

        TypedQuery<MediaEntity> q = entityManager.createQuery(jpql.toString(), MediaEntity.class)
                .setParameter("orgId", query.tenant().orgId())
                .setParameter("projectId", query.tenant().projectId());
        if (query.mediaType() != null) {
            q.setParameter("mediaType", query.mediaType().name());
        }
        if (cursor != null) {
            q.setParameter("cursorTime", cursor.createdAt());
            q.setParameter("cursorId", cursor.id());
        }
        // One extra row tells us whether another page exists without a COUNT(*).
        q.setMaxResults(query.limit() + 1);
        return new ArrayList<>(q.getResultList());
    }

    public String nextCursor(MediaEntity last) {
        Instant createdAt = last.getCreatedAt();
        return new Cursor(createdAt, last.getId()).encode();
    }
}
