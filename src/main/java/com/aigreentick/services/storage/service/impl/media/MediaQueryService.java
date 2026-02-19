
package com.aigreentick.services.storage.service.impl.media;


import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles all read operations on Media entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaQueryService {

    private final MediaRepository mediaRepository;

    @Cacheable(value = "media", key = "'mediaId:' + #mediaId", unless = "#result == null")
    public Optional<Media> findByMediaId(String mediaId) {
        return mediaRepository.findByMediaId(mediaId);
    }

    public Optional<Media> findByStoredFilename(String storedFilename) {
        return mediaRepository.findByStoredFilename(storedFilename);
    }

    public Page<Media> findByOrgAndProject(Long orgId, Long projectId, Pageable pageable) {
        return mediaRepository.findByOrganisationIdAndProjectIdOrderByCreatedAtDesc(orgId, projectId, pageable);
    }

    public Page<Media> findByOrgAndProjectAndType(Long orgId, Long projectId, MediaType type, Pageable pageable) {
        return mediaRepository.findByOrganisationIdAndProjectIdAndMediaTypeOrderByCreatedAtDesc(
                orgId, projectId, type, pageable);
    }

    public boolean existsByStoredFilename(String storedFilename) {
        return mediaRepository.existsByStoredFilename(storedFilename);
    }

    public boolean existsByChecksumAndOrgAndProject(String checksum, Long orgId, Long projectId) {
        return mediaRepository.existsByChecksumAndOrganisationIdAndProjectId(checksum, orgId, projectId);
    }

    public long countByOrgAndProject(Long orgId, Long projectId) {
        return mediaRepository.countByOrganisationIdAndProjectId(orgId, projectId);
    }
}