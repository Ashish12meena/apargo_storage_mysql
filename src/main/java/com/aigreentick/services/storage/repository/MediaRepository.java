package com.aigreentick.services.storage.repository;

import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.enums.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long>, MediaRepositoryCustom {

    Optional<Media> findByStoredFilename(String storedFilename);

    Optional<Media> findByMediaId(String mediaId);

    Page<Media> findByOrganisationIdAndProjectIdOrderByCreatedAtDesc(
            Long organisationId, Long projectId, Pageable pageable);

    Page<Media> findByOrganisationIdAndProjectIdAndMediaTypeOrderByCreatedAtDesc(
            Long organisationId, Long projectId, MediaType mediaType, Pageable pageable);

    boolean existsByStoredFilename(String storedFilename);

    /** Used for duplicate detection */
    boolean existsByChecksumAndOrganisationIdAndProjectId(
            String checksum, Long organisationId, Long projectId);

    long countByOrganisationIdAndProjectId(Long organisationId, Long projectId);

    long countByOrganisationIdAndProjectIdAndMediaType(
            Long organisationId, Long projectId, MediaType mediaType);

    @Query("SELECT m.mediaId FROM Media m WHERE m.id = :id")
    String findMediaIdById(@Param("id") Long id);

    void deleteByOrganisationIdAndProjectId(Long organisationId, Long projectId);

    void deleteByOrganisationId(Long organisationId);
}
