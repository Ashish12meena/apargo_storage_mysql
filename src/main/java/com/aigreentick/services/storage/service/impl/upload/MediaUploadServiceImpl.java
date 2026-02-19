package com.aigreentick.services.storage.service.impl.upload;
import com.aigreentick.services.storage.context.UserContext;
import com.aigreentick.services.storage.dto.response.MediaUploadResponse;
import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.exception.MediaUploadException;
import com.aigreentick.services.storage.exception.StorageException;
import com.aigreentick.services.storage.service.StorageProvider;
import com.aigreentick.services.storage.validator.MediaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;

/**
 * Refactored MediaUploadService using StorageProvider abstraction
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadServiceImpl {
    
    private final StorageProvider storageProvider;
    private final MediaValidator mediaValidator;
    
    public MediaUploadResponse uploadMedia(MultipartFile file) {
        log.info("Starting media upload for file: {} using provider: {}", 
                 file.getOriginalFilename(), 
                 storageProvider.getProviderType());
        
        try { 
            // Validate the file
            mediaValidator.validateFile(file);

            // Determine media type
            String contentType = file.getContentType();
            MediaType mediaType = mediaValidator.determineMediaType(contentType);

            // Build storage metadata
            StorageMetadata metadata = StorageMetadata.builder()
                    .originalFilename(file.getOriginalFilename())
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .projectId(UserContext.getProjectId())
                    .organisationId(UserContext.getOrganisationId())
                    .mediaType(mediaType)
                    .fileExtension(extractFileExtension(file.getOriginalFilename()))
                    .build();

            // Upload to storage provider
            InputStream inputStream = file.getInputStream();
            StorageResult storageResult = storageProvider.save(inputStream, metadata);

            log.info("Successfully uploaded media: {} to provider: {}", 
                     storageResult.getStorageKey(), 
                     storageResult.getProvider());

            // Build and return response
            return MediaUploadResponse.builder()
                    .url(storageResult.getPublicUrl())
                    .originalFilename(file.getOriginalFilename())
                    .storedFilename(storageResult.getStorageKey())
                    .mediaType(mediaType)
                    .contentType(contentType)
                    .fileSizeBytes(file.getSize())
                    .uploadedAt(Instant.now())
                    .build();

        } catch (StorageException e) {
            log.error("Storage provider error during upload: {}", e.getMessage(), e);
            throw new MediaUploadException("Failed to upload file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during media upload: {}", file.getOriginalFilename(), e);
            throw new MediaUploadException("Unexpected error during upload: " + e.getMessage(), e);
        }
    }

    public InputStream getMedia(String storageKey) {
        log.info("Retrieving media: {} from provider: {}", storageKey, storageProvider.getProviderType());

        try {
            return storageProvider.retrieve(storageKey);
        } catch (StorageException e) {
            log.error("Failed to retrieve media: {}", storageKey, e);
            throw new MediaUploadException("Failed to retrieve media: " + storageKey, e);
        }
    }

    public boolean deleteMedia(String storageKey) {
        log.info("Deleting media: {} from provider: {}", storageKey, storageProvider.getProviderType());

        try {
            return storageProvider.delete(storageKey);
        } catch (StorageException e) {
            log.error("Failed to delete media: {}", storageKey, e);
            throw new MediaUploadException("Failed to delete media: " + storageKey, e);
        }
    }

    public boolean mediaExists(String storageKey) {
        return storageProvider.exists(storageKey);
    }

    public String getPublicUrl(String storageKey, java.time.Duration expiry) {
        return storageProvider.getPublicUrl(storageKey, expiry);
    }

    private String extractFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}