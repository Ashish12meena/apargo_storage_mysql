package com.aigreentick.services.storage.service.impl.media;



import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.exception.MediaNotFoundException;
import com.aigreentick.services.storage.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles all write operations on Media entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCommandService {

    private final MediaRepository mediaRepository;

    @Transactional
    public Media save(Media media) {
        return mediaRepository.save(media);
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
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteByOrgAndProject(Long orgId, Long projectId) {
        log.info("Bulk deleting media for org={} project={}", orgId, projectId);
        mediaRepository.deleteByOrganisationIdAndProjectId(orgId, projectId);
    }

    @Transactional
    @CacheEvict(value = {"media", "userMediaList"}, allEntries = true)
    public void deleteByOrganisation(Long orgId) {
        log.info("Bulk deleting media for org={}", orgId);
        mediaRepository.deleteByOrganisationId(orgId);
    }

    @Transactional
    public int softDeleteById(Long mediaId, Long deletedBy) {
        int updated = mediaRepository.softDeleteById(mediaId, deletedBy);
        if (updated == 0) throw new MediaNotFoundException("Media not found: " + mediaId);
        return updated;
    }
}
