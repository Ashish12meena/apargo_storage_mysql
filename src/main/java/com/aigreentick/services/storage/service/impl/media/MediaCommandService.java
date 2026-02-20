package com.aigreentick.services.storage.service.impl.media;

import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.exception.MediaNotFoundException;
import com.aigreentick.services.storage.repository.MediaRepository;
import com.aigreentick.services.storage.service.impl.quota.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles all write operations on Media entities.
 * Quota release is done in the same transaction as the delete
 * so counters stay consistent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCommandService {

    private final MediaRepository mediaRepository;
    private final QuotaService quotaService;

    @Transactional
    public Media save(Media media) {
        return mediaRepository.save(media);
    }

    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteById(Long id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException("Media not found with ID: " + id));

        // Release quota in the same transaction
        quotaService.releaseQuota(media.getOrganisationId(), media.getProjectId(), media.getFileSize());

        mediaRepository.deleteById(id);
        log.info("Deleted media id={} and released {} bytes for org={} project={}",
                id, media.getFileSize(), media.getOrganisationId(), media.getProjectId());
    }

    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public int softDeleteById(Long mediaId, Long deletedBy) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + mediaId));

        // Release quota
        quotaService.releaseQuota(media.getOrganisationId(), media.getProjectId(), media.getFileSize());

        int updated = mediaRepository.softDeleteById(mediaId, deletedBy);
        if (updated == 0) throw new MediaNotFoundException("Media not found: " + mediaId);

        log.info("Soft-deleted media id={} and released {} bytes", mediaId, media.getFileSize());
        return updated;
    }

    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteByOrgAndProject(Long orgId, Long projectId) {
        log.info("Bulk deleting media for org={} project={}", orgId, projectId);
        // Note: For bulk deletes, quota is corrected by the nightly reconciliation job.
        // A more precise approach would sum file sizes first, but for bulk ops
        // the reconciliation job handles it.
        mediaRepository.deleteByOrganisationIdAndProjectId(orgId, projectId);
    }

    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteByOrganisation(Long orgId) {
        log.info("Bulk deleting media for org={}", orgId);
        mediaRepository.deleteByOrganisationId(orgId);
    }
}