package com.aigreentick.services.storage.infrastructure.persistence.mapper;

import com.aigreentick.services.storage.domain.media.Checksum;
import com.aigreentick.services.storage.domain.media.ContentType;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.media.MediaType;
import com.aigreentick.services.storage.domain.media.ScanStatus;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.infrastructure.persistence.entity.MediaEntity;
import org.springframework.stereotype.Component;

/** Entity ↔ domain. The only place both models are visible. */
@Component
public class MediaEntityMapper {

    public Media toDomain(MediaEntity e) {
        return Media.rehydrate(
                e.getId() == null ? null : MediaId.of(e.getId()),
                new TenantRef(e.getOrganisationId(), e.getProjectId()),
                new StorageKey(e.getStorageKey()),
                e.getOriginalFilename(),
                new ContentType(e.getMimeType(),
                        e.getDetectedMimeType() == null ? e.getMimeType() : e.getDetectedMimeType()),
                ByteSize.of(e.getFileSize()),
                MediaType.valueOf(e.getMediaType()),
                MediaStatus.valueOf(e.getStatus()),
                ScanStatus.valueOf(e.getScanStatus()),
                Checksum.ofNullable(e.getChecksumSha256()),
                e.getUploadSessionId(),
                e.getCreatedBy(),
                e.getDeletedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getDeletedAt(),
                e.getPurgeAfter(),
                e.getVersion() == null ? 0L : e.getVersion());
    }

    /** Applies domain state onto a managed or new entity. */
    public void apply(Media media, MediaEntity e, String storageProvider) {
        e.setOrganisationId(media.tenant().orgId());
        e.setProjectId(media.tenant().projectId());
        e.setCreatedBy(media.createdBy());
        e.setOriginalFilename(media.originalFilename());
        e.setStorageKey(media.storageKey().value());
        // stored_filename holds the generated object name, never returned to clients.
        e.setStoredFilename(media.storageKey().filename());
        e.setMimeType(media.contentType().declared());
        e.setDetectedMimeType(media.contentType().detected());
        e.setMediaType(media.mediaType().name());
        e.setFileSize(media.billableSize().value());
        e.setChecksumSha256(media.checksum() == null ? null : media.checksum().sha256Hex());
        e.setStorageProvider(storageProvider);
        e.setStatus(media.status().name());
        e.setScanStatus(media.scanStatus().name());
        e.setUploadSessionId(media.uploadSessionId());
        e.setCreatedAt(media.createdAt());
        e.setUpdatedAt(media.updatedAt());
        e.setDeletedAt(media.deletedAt());
        e.setDeletedBy(media.deletedBy());
        e.setPurgeAfter(media.purgeAfter());
    }
}
