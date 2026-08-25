package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.UploadSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface UploadSessionJpaRepository extends JpaRepository<UploadSessionEntity, String> {

    Optional<UploadSessionEntity> findByIdAndOrgIdAndProjectId(String id, Long orgId, Long projectId);

    /** Hits {@code idx_session_sweep}; without it the sweep degrades to a full scan. */
    @Query("SELECT s FROM UploadSessionEntity s WHERE s.status = 'RESERVED' AND s.expiresAt < :now "
            + "ORDER BY s.expiresAt ASC")
    List<UploadSessionEntity> findReclaimable(@Param("now") Instant now, Pageable pageable);
}
