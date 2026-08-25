package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.command.BatchProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.ProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.BatchUploadMediaUseCase;
import com.aigreentick.services.storage.application.port.in.UploadMediaUseCase;
import com.aigreentick.services.storage.application.port.in.result.BatchUploadView;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.domain.exception.BatchTooLargeException;
import com.aigreentick.services.storage.domain.exception.DomainException;
import com.aigreentick.services.storage.domain.exception.InvalidBatchException;
import com.aigreentick.services.storage.domain.exception.StorageOperationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Collapses N single-file uploads into one request.
 *
 * <p>This class contains NO upload logic. It is a loop, a try/catch, two counters
 * and a result envelope; every file goes through {@link UploadMediaUseCase#uploadProxied}
 * — the same validation, inspection, quota reservation, storage write and
 * activation that {@code POST /upload} uses. That is not a claim to be verified by
 * reading two implementations and comparing them; it is the only implementation
 * there is.
 *
 * <p><b>Sequential, deliberately.</b> Files in a batch share a tenant, so their
 * quota reservations contend on the same row and parallel workers would serialise
 * on that lock anyway. Request-scoped context lives in {@code ThreadLocal}s that a
 * worker pool would not see. And the connection pool (30) sits far below the
 * servlet thread count (200) on the assumption that requests do not each hold
 * several connections — concurrent batches are the fastest route to exhausting it.
 * The win is one HTTP request instead of N, and that is delivered by this endpoint
 * existing rather than by concurrency inside it.
 *
 * <p><b>Per-file idempotency, not batch atomicity.</b> A replayed batch re-serves
 * what already completed and re-runs what did not, creating no duplicate records.
 * It is not a transaction: a batch that half-succeeded stays half-succeeded. See
 * docs/17-risks-assumptions.md.
 */
@Service
@Slf4j
public class MediaBatchUploadService implements BatchUploadMediaUseCase {

    /**
     * Consecutive storage failures after which the rest of the batch is skipped.
     *
     * <p>Hammering a dead backend once per file turns one outage into thread
     * exhaustion: twenty files each waiting out a storage timeout holds a servlet
     * thread and a connection for twenty times the timeout.
     */
    private static final int STORAGE_FAILURE_CIRCUIT = 3;

    private static final String MDC_BATCH_ID = "batchId";

    private final UploadMediaUseCase uploadUseCase;
    private final StorageProperties storageProperties;
    private final MeterRegistry meters;

    public MediaBatchUploadService(UploadMediaUseCase uploadUseCase,
                                   StorageProperties storageProperties,
                                   MeterRegistry meters) {
        this.uploadUseCase = uploadUseCase;
        this.storageProperties = storageProperties;
        this.meters = meters;
    }

    @Override
    public BatchUploadView uploadBatch(BatchProxiedUploadCommand command) {
        List<BatchProxiedUploadCommand.FileItem> files = command.files();

        // Request-level rejections. Neither is a batch result: a caller whose
        // multipart field name is wrong must not receive a success envelope
        // reporting that zero files were processed.
        if (files.isEmpty()) {
            throw new InvalidBatchException("batch contains no files");
        }
        int maximum = storageProperties.maxFilesPerBatch();
        if (files.size() > maximum) {
            throw new BatchTooLargeException(files.size(), maximum);
        }

        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put(MDC_BATCH_ID, batchId);
        Timer.Sample sample = Timer.start(meters);
        long startedAt = System.nanoTime();

        try {
            return process(command, files, batchId);
        } finally {
            sample.stop(meters.timer("storage.upload.batch.duration"));
            // Cleared on every path: the MDC outlives this call on a pooled
            // thread, and a stale batch id on an unrelated request is worse than
            // no batch id at all.
            MDC.remove(MDC_BATCH_ID);
            if (log.isDebugEnabled()) {
                log.debug("batch {} took {} ms", batchId, (System.nanoTime() - startedAt) / 1_000_000);
            }
        }
    }

    private BatchUploadView process(BatchProxiedUploadCommand command,
                                    List<BatchProxiedUploadCommand.FileItem> files,
                                    String batchId) {

        List<BatchUploadView.ItemView> results = new ArrayList<>(files.size());
        int successCount = 0;
        int failedCount = 0;
        int consecutiveStorageFailures = 0;
        boolean circuitOpen = false;
        long startedAt = System.currentTimeMillis();

        for (int index = 0; index < files.size(); index++) {
            BatchProxiedUploadCommand.FileItem file = files.get(index);

            if (circuitOpen) {
                results.add(BatchUploadView.ItemView.skipped(file.originalFilename(),
                        ErrorCode.BATCH_ITEM_SKIPPED.name(),
                        ErrorCode.BATCH_ITEM_SKIPPED.defaultMessage()));
                failedCount++;
                countFile("skipped");
                continue;
            }

            try {
                MediaView view = uploadUseCase.uploadProxied(new ProxiedUploadCommand(
                        command.tenant(), command.actor(), file.originalFilename(),
                        file.declaredContentType(), file.size(), file.content(),
                        command.idempotencyKeyForIndex(index)));

                results.add(BatchUploadView.ItemView.success(file.originalFilename(), view));
                successCount++;
                consecutiveStorageFailures = 0;
                countFile("success");

            } catch (StorageOperationException e) {
                // Counted separately from other domain failures: repeated storage
                // failures mean the backend is down, not that these files are bad.
                consecutiveStorageFailures++;
                log.warn("batch {} file {} ({}) failed against storage: {}",
                        batchId, index, file.originalFilename(), e.getMessage());

                results.add(BatchUploadView.ItemView.failed(file.originalFilename(),
                        e.errorCode().name(), e.clientMessage()));
                failedCount++;
                countFile("failed");

                if (consecutiveStorageFailures >= STORAGE_FAILURE_CIRCUIT) {
                    log.error("batch {} abandoning after {} consecutive storage failures; "
                            + "remaining files skipped", batchId, consecutiveStorageFailures);
                    circuitOpen = true;
                }

            } catch (DomainException e) {
                // Expected business outcome — wrong type, too large, quota gone.
                // The internal message goes to the log; the client gets the safe one.
                log.debug("batch {} file {} ({}) rejected: {}",
                        batchId, index, file.originalFilename(), e.getMessage());

                results.add(BatchUploadView.ItemView.failed(file.originalFilename(),
                        e.errorCode().name(), e.clientMessage()));
                failedCount++;
                consecutiveStorageFailures = 0;
                countFile("failed");

            } catch (RuntimeException e) {
                // Unexpected. Full detail with the stack trace to the log, a
                // generic code to the client — never the exception message, which
                // may carry a storage key or a provider response.
                log.error("batch {} file {} ({}) failed unexpectedly [trace={}]",
                        batchId, index, file.originalFilename(),
                        RequestContext.traceIdOrNull(), e);

                results.add(BatchUploadView.ItemView.failed(file.originalFilename(),
                        ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
                failedCount++;
                consecutiveStorageFailures = 0;
                countFile("failed");
            }
        }

        // ONE line per batch, not one per file: twenty info lines per request
        // drowns the signal on what will be the busiest write path in the service.
        log.info("batch {} complete: {} succeeded, {} failed, {} submitted, {} ms",
                batchId, successCount, failedCount, files.size(),
                System.currentTimeMillis() - startedAt);

        return new BatchUploadView(successCount, failedCount, results);
    }

    private void countFile(String outcome) {
        meters.counter("storage.upload.batch.files", "outcome", outcome).increment();
    }
}
