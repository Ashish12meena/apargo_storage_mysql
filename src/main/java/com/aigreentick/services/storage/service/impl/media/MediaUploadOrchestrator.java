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
import com.aigreentick.services.storage.integration.organisation.OrganisationClient;
import com.aigreentick.services.storage.integration.organisation.dto.AccessTokenCredentials;
import com.aigreentick.services.storage.mapper.MediaMapper;
import com.aigreentick.services.storage.integration.facebook.dto.WhatsappMediaUploadResponse;
import com.aigreentick.services.storage.service.impl.quota.QuotaService;
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
 * validate → quota check (local DB) → convert to temp file → store → persist.
 *
 * Quota is enforced entirely within the Storage Service using local
 * org_storage and project_storage tables. No remote service call needed.
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
    private final QuotaService quotaService;

    /**
     * Full upload flow inside a single transaction:
     * 1. Validate file
     * 2. Reserve quota (locks project row then org row, checks capacity, increments used_bytes)
     * 3. Save to storage provider
     * 4. Insert Media entity
     * 5. Commit — quota + media row committed atomically
     *
     * On any failure the transaction rolls back, releasing the reserved quota automatically.
     */
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
            // 1. Reserve quota — this acquires pessimistic locks and increments counters.
            //    If quota is exceeded, StorageLimitExceededException is thrown and
            //    the transaction rolls back (counters are never committed).
            quotaService.reserveQuota(orgId, projectId, multipart.getSize());

            String contentType = multipart.getContentType();
            MediaType mediaType = mediaValidator.detectMediaType(contentType);

            // 2. Convert MultipartFile to temp file ONCE
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

            // 3. Persist to storage provider
            StorageResult storageResult = saveToStorage(tempFile, metadata);

            // 4. (Optional) Upload to WhatsApp — best-effort, does not block
            String whatsappMediaId = null;
            try {
                whatsappMediaId = pushToWhatsApp(tempFile, contentType, projectId, wabaId);
            } catch (Exception ex) {
                log.warn("WhatsApp upload skipped for file='{}': {}",
                        multipart.getOriginalFilename(), ex.getMessage());
            }

            // 5. Insert Media entity (same transaction as quota reservation)
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
                    .mediaId(whatsappMediaId)
                    .organisationId(orgId)
                    .projectId(projectId)
                    .status(MediaStatus.ACTIVE)
                    .createdAt(now)
                    .build();

            commandService.save(media);

            log.info("Upload complete. key={} provider={} org={} project={}",
                    storageResult.getStorageKey(), storageResult.getProvider(), orgId, projectId);

            return MediaUploadResponse.builder()
                    .url(storageResult.getPublicUrl())
                    .originalFilename(multipart.getOriginalFilename())
                    .storedFilename(storageResult.getStorageKey())
                    .mediaType(mediaType)
                    .contentType(contentType)
                    .mediaId(whatsappMediaId)
                    .fileSizeBytes(multipart.getSize())
                    .uploadedAt(now)
                    .build();

        } catch (MediaValidationException | StorageLimitExceededException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Media upload failed for file='{}'", multipart.getOriginalFilename(), ex);
            throw new MediaUploadException("Media upload failed: " + ex.getMessage(), ex);
        } finally {
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

    private StorageResult saveToStorage(File tempFile, StorageMetadata metadata) {
        try (InputStream is = new FileInputStream(tempFile)) {
            return storagePort.save(is, metadata);
        } catch (IOException ex) {
            log.error("Failed to open temp file for storage upload: {}", tempFile.getAbsolutePath(), ex);
            throw new MediaUploadException("Failed to read temp file for storage: " + ex.getMessage(), ex);
        }
    }

    private String pushToWhatsApp(File file, String contentType, Long projectId, String wabaId) {
        AccessTokenCredentials creds = organisationClient.getPhoneNumberCredentials(projectId, wabaId);
        FacebookApiResult<WhatsappMediaUploadResponse> result =
                facebookClient.uploadMedia(file, contentType, creds.getId(), creds.getAccessToken());

        if (!result.isSuccess()) {
            throw new MediaUploadException(result.getErrorMessage(), result.getStatusCode());
        }
        return result.getData().getId();
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