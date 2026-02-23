package com.aigreentick.services.storage.service.impl.media;

import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.exception.MediaNotFoundException;
import com.aigreentick.services.storage.repository.MediaRepository;
import com.aigreentick.services.storage.service.impl.quota.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void deleteById(Long id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException("Media not found with ID: " + id));

        quotaService.releaseQuota(media.getOrganisationId(), media.getProjectId(), media.getFileSize());
        mediaRepository.deleteById(id);

        log.info("Deleted media id={} released {} bytes org={} project={}",
                id, media.getFileSize(), media.getOrganisationId(), media.getProjectId());
    }

    @Transactional
    public int softDeleteById(Long mediaId, Long deletedBy) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + mediaId));

        quotaService.releaseQuota(media.getOrganisationId(), media.getProjectId(), media.getFileSize());

        int updated = mediaRepository.softDeleteById(mediaId, deletedBy);
        if (updated == 0) throw new MediaNotFoundException("Media not found: " + mediaId);

        log.info("Soft-deleted media id={} released {} bytes", mediaId, media.getFileSize());
        return updated;
    }

    @Transactional
    public void deleteByOrgAndProject(Long orgId, Long projectId) {
        log.info("Bulk deleting media for org={} project={}", orgId, projectId);
        mediaRepository.deleteByOrganisationIdAndProjectId(orgId, projectId);
    }

    @Transactional
    public void deleteByOrganisation(Long orgId) {
        log.info("Bulk deleting media for org={}", orgId);
        mediaRepository.deleteByOrganisationId(orgId);
    }
}