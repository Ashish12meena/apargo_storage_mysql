
package com.aigreentick.services.storage.repository.impl;

import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.enums.MediaStatus;
import com.aigreentick.services.storage.repository.MediaRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public class MediaRepositoryImpl implements MediaRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public int softDeleteById(Long mediaId, Long deletedBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<Media> update = cb.createCriteriaUpdate(Media.class);
        Root<Media> root = update.from(Media.class);

        update.set(root.get("status"), MediaStatus.DELETED);   // extend enum with DELETED if needed
        update.set(root.get("deletedAt"), Instant.now());
        update.set(root.get("updatedAt"), Instant.now());
        update.where(cb.equal(root.get("id"), mediaId));

        return entityManager.createQuery(update).executeUpdate();
    }
}
