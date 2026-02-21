package com.aigreentick.services.storage.service.impl.media;

import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.dto.response.MediaUploadResponse;
import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.MediaStatus;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.exception.MediaUploadException;
import com.aigreentick.services.storage.exception.MediaValidationException;
import com.aigreentick.services.storage.exception.StorageLimitExceededException;
import com.aigreentick.services.storage.integration.account.WhatsappAccountClient;
import com.aigreentick.services.storage.integration.account.dto.AccessTokenCredentials;
import com.aigreentick.services.storage.integration.facebook.FacebookApiResult;
import com.aigreentick.services.storage.integration.facebook.FacebookMediaClient;
import com.aigreentick.services.storage.integration.facebook.dto.WhatsappMediaUploadResponse;
import com.aigreentick.services.storage.service.impl.quota.OptimisticQuotaService;
import com.aigreentick.services.storage.service.port.StoragePort;
import com.aigreentick.services.storage.util.FileUtils;
import com.aigreentick.services.storage.validator.MediaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Async media upload orchestrator.
 *
 * Key difference from the synchronous version:
 * - orgId and projectId are passed as METHOD PARAMETERS (not ThreadLocal)
 * - Quota uses optimistic locking (no blocking)
 * - Runs on dedicated "mediaUploadExecutor" thread pool
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrentMediaUploadService {

    private final StoragePort storagePort;
    private final FacebookMediaClient facebookClient;
    private final WhatsappAccountClient organisationClient;
    private final MediaCommandService commandService;
    private final MediaValidator mediaValidator;
    private final OptimisticQuotaService quotaService;

    /**
     * Synchronous wrapper — called from controller.
     * Validates on the HTTP thread (ThreadLocal still works here),
     * then delegates to the actual upload logic with explicit context.
     */
    public MediaUploadResponse uploadMediaSync(MultipartFile multipart, String wabaId,
                                                Long orgId, Long projectId) {
        if (multipart == null || multipart.isEmpty()) {
            throw new MediaValidationException("Uploaded file is empty or null");
        }
        mediaValidator.validateFile(multipart);
        return doUpload(multipart, wabaId, orgId, projectId);
    }

    /**
     * Async variant — returns CompletableFuture for batch/parallel scenarios.
     * Context passed explicitly since @Async runs on a different thread.
     */
    @Async("mediaUploadExecutor")
    public CompletableFuture<MediaUploadResponse> uploadMediaAsync(
            MultipartFile multipart, String wabaId, Long orgId, Long projectId) {
        try {
            MediaUploadResponse resp = doUpload(multipart, wabaId, orgId, projectId);
            return CompletableFuture.completedFuture(resp);
        } catch (Exception ex) {
            CompletableFuture<MediaUploadResponse> f = new CompletableFuture<>();
            f.completeExceptionally(ex);
            return f;
        }
    }

    // ── Core upload logic ─────────────────────────────────────────────────────

    private MediaUploadResponse doUpload(MultipartFile multipart, String wabaId,
                                          Long orgId, Long projectId) {
        long startTime = System.currentTimeMillis();
        File tempFile = null;

        try {
            String contentType = multipart.getContentType();
            MediaType mediaType = mediaValidator.detectMediaType(contentType);

            // 1. Reserve quota — optimistic lock + retry, runs in its own tx
            quotaService.reserveQuota(orgId, projectId, multipart.getSize());

            boolean quotaReserved = true;
            try {
                // 2. Convert to temp file ONCE
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

                // 4. (Optional) Upload to WhatsApp — best-effort
                // String whatsappMediaId = null;
                // try {
                //     whatsappMediaId = pushToWhatsApp(tempFile, contentType, projectId, wabaId);
                // } catch (Exception ex) {
                //     log.warn("WhatsApp upload skipped for file='{}': {}",
                //             multipart.getOriginalFilename(), ex.getMessage());
                // }

                // 5. Insert Media entity
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

                long duration = System.currentTimeMillis() - startTime;
                log.info("Upload complete: key={} provider={} org={} project={} duration={}ms",
                        storageResult.getStorageKey(), storageResult.getProvider(),
                        orgId, projectId, duration);

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
                throw ex;
            } catch (Exception ex) {
                // Rollback quota on failure
                if (quotaReserved) {
                    try {
                        quotaService.releaseQuota(orgId, projectId, multipart.getSize());
                        log.info("Quota rolled back after upload failure: org={} project={} size={}",
                                orgId, projectId, multipart.getSize());
                    } catch (Exception rollbackEx) {
                        log.error("Failed to rollback quota: org={} project={}", orgId, projectId, rollbackEx);
                        // Nightly reconciliation will fix this
                    }
                }
                throw ex;
            }

        } catch (MediaValidationException | StorageLimitExceededException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Media upload failed: file='{}' org={} project={}",
                    multipart.getOriginalFilename(), orgId, projectId, ex);
            throw new MediaUploadException("Media upload failed: " + ex.getMessage(), ex);
        } finally {
            FileUtils.deleteQuietly(tempFile);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private StorageResult saveToStorage(File tempFile, StorageMetadata metadata) {
        try (InputStream is = new FileInputStream(tempFile)) {
            return storagePort.save(is, metadata);
        } catch (IOException ex) {
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

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}