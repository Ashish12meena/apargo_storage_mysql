package com.aigreentick.services.storage.mapper;

import com.aigreentick.services.storage.context.UserContext;
import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.dto.response.UserMediaResponse;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.MediaType;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MediaMapper {

    public UserMediaResponse toUserMediaResponse(Media media) {
        if (media == null) return null;

        return UserMediaResponse.builder()
                .id(media.getId())
                .url(media.getMediaUrl())
                .originalFilename(media.getOriginalFilename())
                .storedFilename(media.getStoredFilename())
                .mediaType(media.getMediaType())
                .contentType(media.getMimeType())
                .mediaId(media.getMediaId())
                .fileSizeBytes(media.getFileSize())
                .uploadedAt(media.getCreatedAt())
                .build();
    }

    public Media toEntity(StorageResult storageResult, String originalFilename,
                          MediaType mediaType, String contentType,
                          Long fileSizeBytes, Instant uploadedAt) {
        return Media.builder()
                .mediaUrl(storageResult.getPublicUrl())
                .originalFilename(originalFilename)
                .storedFilename(storageResult.getStorageKey())
                .mediaType(mediaType)
                .mimeType(contentType)
                .fileSize(fileSizeBytes)
                .storageProvider(storageResult.getProvider())
                .storageBucket(storageResult.getBucket())
                .storageKey(storageResult.getStorageKey())
                .storageRegion(storageResult.getRegion())
                .organisationId(UserContext.getOrganisationId())
                .projectId(UserContext.getProjectId())
                .createdAt(uploadedAt)
                .build();
    }
}