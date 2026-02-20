package com.aigreentick.services.storage.service.impl.media;

import com.aigreentick.services.storage.context.UserContext;
import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.dto.response.MediaUploadResponse;
import com.aigreentick.services.storage.dto.response.UserMediaResponse;
import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.MediaStatus;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.exception.MediaUploadException;
import com.aigreentick.services.storage.exception.MediaValidationException;
import com.aigreentick.services.storage.exception.StorageLimitExceededException;
import com.aigreentick.services.storage.integration.facebook.FacebookApiResult;
import com.aigreentick.services.storage.integration.facebook.FacebookMediaClient;
import com.aigreentick.services.storage.integration.facebook.dto.WhatsappMediaUploadResponse;
import com.aigreentick.services.storage.integration.organisation.OrganisationClient;
import com.aigreentick.services.storage.integration.organisation.dto.AccessTokenCredentials;
import com.aigreentick.services.storage.integration.organisation.dto.StorageInfo;
import com.aigreentick.services.storage.mapper.MediaMapper;
import com.aigreentick.services.storage.service.port.StoragePort;
import com.aigreentick.services.storage.util.FileUtils;
import com.aigreentick.services.storage.validator.MediaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * Orchestrates the full media upload flow:
 * validate → quota check → convert to temp file → store → upload to WhatsApp → persist.
 *
 * FIX: MultipartFile InputStream can only be read once. Previously, storagePort.save()
 * consumed the stream, leaving convertMultipartToFile() with an empty/partial file for
 * the WhatsApp upload. Now we convert to a temp file first and derive an independent
 * FileInputStream for storage, while reusing the same File for WhatsApp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadOrchestrator {

    private final StoragePort storagePort;
    private final FacebookMediaClient facebookClient;
    private final OrganisationClient organisationClient;
    private final MediaCommandService commandService;
    private final MediaQueryService queryService;
    private final MediaMapper mediaMapper;
    private final MediaValidator mediaValidator;

    @Transactional
    public MediaUploadResponse uploadMedia(MultipartFile multipart, String wabaId) {
        if (multipart == null || multipart.isEmpty()) {
            throw new MediaValidationException("Uploaded file is empty or null");
        }

        mediaValidator.validateFile(multipart);

        Long orgId = requireOrgId();
        Long projectId = requireProjectId();

        File tempFile = null;
        try {
            // 1. Quota check
            enforceStorageQuota(multipart.getSize());

            String contentType = multipart.getContentType();
            MediaType mediaType = mediaValidator.detectMediaType(contentType);

            // 2. Convert MultipartFile to temp file ONCE.
            //    This is the single read of the underlying servlet stream.
            //    Both storagePort and WhatsApp upload will reuse this file.
            tempFile = FileUtils.convertMultipartToFile(multipart);

            StorageMetadata metadata = StorageMetadata.builder()
                    .originalFilename(multipart.getOriginalFilename())
                    .contentType(contentType)
                    .fileSize(multipart.getSize())
                    .organisationId(orgId)
                    .projectId(projectId)
                    .mediaType(mediaType)
                    .fileExtension(extractExtension(multipart.getOriginalFilename()))
                    .build();

            // 3. Persist to storage provider using a fresh FileInputStream.
            //    This stream is fully independent of the original multipart stream.
            StorageResult storageResult = saveToStorage(tempFile, metadata);

            // 4. Upload to WhatsApp using the same temp File object (not a stream).
            //    Best-effort — failure is logged and does not block the response.
            // String whatsappMediaId = null;
            // try {
            //     whatsappMediaId = pushToWhatsApp(tempFile, contentType, projectId, wabaId);
            // } catch (Exception ex) {
            //     log.warn("WhatsApp upload skipped for file='{}': {}",
            //             multipart.getOriginalFilename(), ex.getMessage());
            // }

            // 5. Persist Media entity
            Instant now = Instant.now();
            Media media = Media.builder()
                    .originalFilename(multipart.getOriginalFilename())
                    .storedFilename(storageResult.getStorageKey())
                    .mimeType(contentType)
                    .fileSize(multipart.getSize())
                    .wabaId(wabaId)
                    .mediaType(mediaType)
                    .storageProvider(storageResult.getProvider())
                    .storageKey(storageResult.getStorageKey())
                    .storageBucket(storageResult.getBucket())
                    .storageRegion(storageResult.getRegion())
                    .mediaUrl(storageResult.getPublicUrl())
                    // .mediaId(whatsappMediaId)
                    .organisationId(orgId)
                    .projectId(projectId)
                    .status(MediaStatus.ACTIVE)
                    .createdAt(now)
                    .build();

            commandService.save(media);

            // log.info("Upload complete. key={} provider={} org={} project={} wabaMediaId={}",
            //         storageResult.getStorageKey(), storageResult.getProvider(),
            //         orgId, projectId, whatsappMediaId);

            return MediaUploadResponse.builder()
                    .url(storageResult.getPublicUrl())
                    .originalFilename(multipart.getOriginalFilename())
                    .storedFilename(storageResult.getStorageKey())
                    .mediaType(mediaType)
                    .contentType(contentType)
                    // .mediaId(whatsappMediaId)
                    .fileSizeBytes(multipart.getSize())
                    .uploadedAt(now)
                    .build();

        } catch (MediaValidationException | StorageLimitExceededException ex) {
            throw ex; // let GlobalExceptionHandler handle known errors
        } catch (Exception ex) {
            log.error("Media upload failed for file='{}'", multipart.getOriginalFilename(), ex);
            throw new MediaUploadException("Media upload failed: " + ex.getMessage(), ex);
        } finally {
            // Always clean up the temp file regardless of success or failure
            FileUtils.deleteQuietly(tempFile);
        }
    }

    @Transactional(readOnly = true)
    public Page<UserMediaResponse> getMedia(Pageable pageable) {
        return queryService
                .findByOrgAndProject(requireOrgId(), requireProjectId(), pageable)
                .map(mediaMapper::toUserMediaResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserMediaResponse> getMediaByType(MediaType type, Pageable pageable) {
        return queryService
                .findByOrgAndProjectAndType(requireOrgId(), requireProjectId(), type, pageable)
                .map(mediaMapper::toUserMediaResponse);
    }

    public String getPublicUrl(String storageKey, Duration duration) {
        return storagePort.getPublicUrl(storageKey, duration);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Opens a fresh FileInputStream from the temp file and delegates to the storage port.
     * The stream is closed in a try-with-resources regardless of outcome.
     */
    private StorageResult saveToStorage(File tempFile, StorageMetadata metadata) {
        try (InputStream is = new FileInputStream(tempFile)) {
            return storagePort.save(is, metadata);
        } catch (IOException ex) {
            log.error("Failed to open temp file for storage upload: {}", tempFile.getAbsolutePath(), ex);
            throw new MediaUploadException("Failed to read temp file for storage: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches phone-number credentials and uploads the file to WhatsApp.
     * Uses the File object directly — no stream involved here.
     */
    private String pushToWhatsApp(File file, String contentType, Long projectId, String wabaId) {
        AccessTokenCredentials creds = organisationClient.getPhoneNumberCredentials(projectId, wabaId);
        FacebookApiResult<WhatsappMediaUploadResponse> result =
                facebookClient.uploadMedia(file, contentType, creds.getId(), creds.getAccessToken());

        if (!result.isSuccess()) {
            throw new MediaUploadException(result.getErrorMessage(), result.getStatusCode());
        }
        return result.getData().getId();
    }

    private void enforceStorageQuota(long fileSize) {
        StorageInfo info = organisationClient.getStorageInfo();
        if (info.getRemaining() < fileSize) {
            throw new StorageLimitExceededException(
                    String.format("Storage quota exceeded. Available: %d bytes, required: %d bytes",
                            info.getRemaining(), fileSize));
        }
    }

    private Long requireOrgId() {
        Long id = UserContext.getOrganisationId();
        if (id == null) throw new MediaValidationException("Organisation context is missing");
        return id;
    }

    private Long requireProjectId() {
        Long id = UserContext.getProjectId();
        if (id == null) throw new MediaValidationException("Project context is missing");
        return id;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}