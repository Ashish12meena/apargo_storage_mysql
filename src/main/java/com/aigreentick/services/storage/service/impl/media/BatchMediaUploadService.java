package com.aigreentick.services.storage.service.impl.media;

import com.aigreentick.services.storage.config.properties.MediaProperties;
import com.aigreentick.services.storage.domain.Media;
import com.aigreentick.services.storage.dto.response.BatchFileResult;
import com.aigreentick.services.storage.dto.response.BatchMediaUploadResponse;
import com.aigreentick.services.storage.dto.response.BatchValidationResult;
import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.MediaStatus;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.exception.MediaValidationException;
import com.aigreentick.services.storage.repository.MediaRepository;
import com.aigreentick.services.storage.service.impl.quota.OptimisticQuotaService;
import com.aigreentick.services.storage.service.port.StoragePort;
import com.aigreentick.services.storage.util.FileUtils;
import com.aigreentick.services.storage.validator.MediaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Batch media upload orchestrator.
 *
 * Flow:
 * 1. Validate each file individually → collect valid + rejected
 * 2. Calculate total size of valid files
 * 3. ONE atomic quota reservation for aggregate size
 * 4. Convert all valid MultipartFiles to temp files
 * 5. Save all files to storage provider IN PARALLEL
 * 6. Batch insert all Media entities (saveAll)
 * 7. Cleanup all temp files
 * 8. Return BatchMediaUploadResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchMediaUploadService {

    private final StoragePort storagePort;
    private final MediaRepository mediaRepository;
    private final MediaValidator mediaValidator;
    private final OptimisticQuotaService quotaService;
    private final MediaProperties mediaProperties;
    private final Executor mediaUploadExecutor;

    public BatchMediaUploadResponse uploadBatch(List<MultipartFile> files, String wabaId,
                                                 Long orgId, Long projectId) {
        long startTime = System.currentTimeMillis();

        // ── Step 0: Aggregate constraints ────────────────────────────────
        if (files == null || files.isEmpty()) {
            throw new MediaValidationException("No files provided in batch");
        }
        if (files.size() > mediaProperties.getBatchMaxFiles()) {
            throw new MediaValidationException(
                    String.format("Batch exceeds max file count. Limit: %d, provided: %d",
                            mediaProperties.getBatchMaxFiles(), files.size()));
        }

        // ── Step 1: Validate each file ───────────────────────────────────
        BatchValidationResult validation = mediaValidator.validateBatch(files);
        List<MultipartFile> validFiles = validation.getValidFiles();
        List<BatchFileResult> results = new ArrayList<>(validation.getRejectedResults());

        if (validFiles.isEmpty()) {
            return BatchMediaUploadResponse.builder()
                    .successCount(0)
                    .failedCount(results.size())
                    .results(results)
                    .build();
        }

        // ── Step 2: Check aggregate size ─────────────────────────────────
        long totalSize = validation.getTotalValidSize();
        if (totalSize > mediaProperties.getBatchMaxTotalSize()) {
            throw new MediaValidationException(
                    String.format("Batch total size exceeds limit. Limit: %d bytes, total: %d bytes",
                            mediaProperties.getBatchMaxTotalSize(), totalSize));
        }

        // ── Step 3: ONE atomic quota reservation ─────────────────────────
        quotaService.reserveQuotaAtomic(orgId, projectId, totalSize);

        List<File> tempFiles = new ArrayList<>();
        long successBytes = 0;

        try {
            // ── Step 4: Convert to temp files ────────────────────────────
            List<MultipartFile> convertedMultiparts = new ArrayList<>();
            List<File> convertedTempFiles = new ArrayList<>();

            for (MultipartFile mf : validFiles) {
                try {
                    File temp = FileUtils.convertMultipartToFile(mf);
                    tempFiles.add(temp);
                    convertedMultiparts.add(mf);
                    convertedTempFiles.add(temp);
                } catch (Exception ex) {
                    results.add(BatchFileResult.failed(mf.getOriginalFilename(),
                            "Failed to process file: " + ex.getMessage()));
                }
            }

            // ── Step 5: Save to storage provider IN PARALLEL ─────────────
            List<CompletableFuture<StorageUploadOutcome>> futures = new ArrayList<>();

            for (int i = 0; i < convertedMultiparts.size(); i++) {
                MultipartFile mf = convertedMultiparts.get(i);
                File tempFile = convertedTempFiles.get(i);

                futures.add(CompletableFuture.supplyAsync(() ->
                        saveOneFile(mf, tempFile, orgId, projectId), mediaUploadExecutor));
            }

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // ── Step 6: Collect results + build entities ─────────────────
            Instant now = Instant.now();
            List<Media> mediaEntities = new ArrayList<>();

            for (int i = 0; i < futures.size(); i++) {
                MultipartFile mf = convertedMultiparts.get(i);
                StorageUploadOutcome outcome;

                try {
                    outcome = futures.get(i).get();
                } catch (Exception ex) {
                    results.add(BatchFileResult.failed(mf.getOriginalFilename(),
                            "Storage failed: " + ex.getMessage()));
                    continue;
                }

                if (!outcome.isSuccess()) {
                    results.add(BatchFileResult.failed(mf.getOriginalFilename(), outcome.getError()));
                    continue;
                }

                StorageResult sr = outcome.getStorageResult();
                MediaType mediaType = mediaValidator.detectMediaType(mf.getContentType());

                Media media = Media.builder()
                        .originalFilename(mf.getOriginalFilename())
                        .storedFilename(sr.getStorageKey())
                        .mimeType(mf.getContentType())
                        .fileSize(mf.getSize())
                        .wabaId(wabaId)
                        .mediaType(mediaType)
                        .storageProvider(sr.getProvider())
                        .storageKey(sr.getStorageKey())
                        .storageBucket(sr.getBucket())
                        .storageRegion(sr.getRegion())
                        .mediaUrl(sr.getPublicUrl())
                        .organisationId(orgId)
                        .projectId(projectId)
                        .status(MediaStatus.ACTIVE)
                        .createdAt(now)
                        .build();

                mediaEntities.add(media);
                successBytes += mf.getSize();

                results.add(BatchFileResult.success(
                        mf.getOriginalFilename(),
                        sr.getPublicUrl(),
                        mediaType,
                        mf.getContentType(),
                        mf.getSize()));
            }

            // ── Step 7: Batch insert ─────────────────────────────────────
            if (!mediaEntities.isEmpty()) {
                mediaRepository.saveAll(mediaEntities);
            }

            // ── Release quota for failed files ───────────────────────────
            long failedBytes = totalSize - successBytes;
            if (failedBytes > 0) {
                try {
                    quotaService.releaseQuotaAtomic(orgId, projectId, failedBytes);
                    log.info("Released {} bytes for failed files in batch", failedBytes);
                } catch (Exception ex) {
                    log.error("Failed to release quota for failed batch files. " +
                              "Nightly reconciliation will fix. failedBytes={}", failedBytes, ex);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            int successCount = (int) results.stream()
                    .filter(r -> r.getStatus() == BatchFileResult.Status.SUCCESS).count();
            int failedCount = results.size() - successCount;

            log.info("Batch upload complete: org={} project={} success={} failed={} duration={}ms",
                    orgId, projectId, successCount, failedCount, duration);

            return BatchMediaUploadResponse.builder()
                    .successCount(successCount)
                    .failedCount(failedCount)
                    .results(results)
                    .build();

        } catch (Exception ex) {
            // Full failure — release all quota
            try {
                quotaService.releaseQuotaAtomic(orgId, projectId, totalSize);
            } catch (Exception rollbackEx) {
                log.error("Failed to rollback batch quota. Nightly reconciliation will fix.", rollbackEx);
            }
            throw ex;
        } finally {
            // ── Step 8: Cleanup temp files ───────────────────────────────
            tempFiles.forEach(FileUtils::deleteQuietly);
        }
    }

    // ── Internal helper: save a single file to storage ───────────────────

    private StorageUploadOutcome saveOneFile(MultipartFile mf, File tempFile,
                                              Long orgId, Long projectId) {
        try {
            MediaType mediaType = mediaValidator.detectMediaType(mf.getContentType());

            StorageMetadata metadata = StorageMetadata.builder()
                    .originalFilename(mf.getOriginalFilename())
                    .contentType(mf.getContentType())
                    .fileSize(mf.getSize())
                    .organisationId(orgId)
                    .projectId(projectId)
                    .mediaType(mediaType)
                    .fileExtension(extractExtension(mf.getOriginalFilename()))
                    .build();

            try (InputStream is = new FileInputStream(tempFile)) {
                StorageResult result = storagePort.save(is, metadata);
                return StorageUploadOutcome.success(result);
            }
        } catch (Exception ex) {
            log.error("Failed to save file '{}' to storage", mf.getOriginalFilename(), ex);
            return StorageUploadOutcome.failed(ex.getMessage());
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    // ── Internal result wrapper ──────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    private static class StorageUploadOutcome {
        private boolean success;
        private StorageResult storageResult;
        private String error;

        static StorageUploadOutcome success(StorageResult result) {
            return StorageUploadOutcome.builder()
                    .success(true).storageResult(result).build();
        }

        static StorageUploadOutcome failed(String error) {
            return StorageUploadOutcome.builder()
                    .success(false).error(error).build();
        }
    }
}