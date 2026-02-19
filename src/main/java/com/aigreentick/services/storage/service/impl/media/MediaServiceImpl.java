package com.aigreentick.services.storage.service.impl.media;

import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.exception.MediaNotFoundException;
import com.aigreentick.services.storage.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MediaServiceImpl  {

    private final MediaRepository mediaRepository;

    @Transactional(readOnly = true)
    public Optional<Media> findByStoredFilename(String storedFilename) {
        return mediaRepository.findByStoredFilename(storedFilename);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "media", key = "'mediaId:' + #mediaId", unless = "#result == null")
    public Optional<Media> findByMediaId(String mediaId) {
        return mediaRepository.findByMediaId(mediaId);
    }

    @Transactional(readOnly = true)
    public Page<Media> findByOrgAndProject(Long orgId, Long projectId, Pageable pageable) {
        log.info("Fetching media for org={} project={}", orgId, projectId);
        return mediaRepository.findByOrganisationIdAndProjectIdOrderByCreatedAtDesc(orgId, projectId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Media> findByOrgAndProjectAndType(Long orgId, Long projectId, MediaType mediaType, Pageable pageable) {
        log.info("Fetching {} media for org={} project={}", mediaType, orgId, projectId);
        return mediaRepository.findByOrganisationIdAndProjectIdAndMediaTypeOrderByCreatedAtDesc(
                orgId, projectId, mediaType, pageable);
    }

    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteById(Long id) {
        if (!mediaRepository.existsById(id)) {
            throw new MediaNotFoundException("Media not found with ID: " + id);
        }
        mediaRepository.deleteById(id);
    }

    @Transactional
    public void deleteByStoredFilename(String storedFilename) {
        Media media = mediaRepository.findByStoredFilename(storedFilename)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + storedFilename));
        mediaRepository.delete(media);
    }

    @Transactional
    public void softDeleteById(Long mediaId, Long deletedBy) {
        if (mediaRepository.softDeleteById(mediaId, deletedBy) == 0) {
            throw new MediaNotFoundException("Media not found: " + mediaId);
        }
    }

    /** Bulk delete all media for a specific project under an org */
    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteByOrgAndProject(Long orgId, Long projectId) {
        log.info("Bulk deleting all media for org={} project={}", orgId, projectId);
        mediaRepository.deleteByOrganisationIdAndProjectId(orgId, projectId);
    }

    /** Bulk delete all media for an entire organization */
    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteByOrganisation(Long orgId) {
        log.info("Bulk deleting all media for org={}", orgId);
        mediaRepository.deleteByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public boolean existsByStoredFilename(String storedFilename) {
        return mediaRepository.existsByStoredFilename(storedFilename);
    }

    @Transactional(readOnly = true)
    public long countByOrgAndProject(Long orgId, Long projectId) {
        return mediaRepository.countByOrganisationIdAndProjectId(orgId, projectId);
    }

    @Transactional(readOnly = true)
    public long countByOrgAndProjectAndType(Long orgId, Long projectId, MediaType mediaType) {
        return mediaRepository.countByOrganisationIdAndProjectIdAndMediaType(orgId, projectId, mediaType);
    }

    public void save(Media media) {
        mediaRepository.save(media);
    }

}