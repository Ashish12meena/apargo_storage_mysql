package com.aigreentick.services.storage.schedular;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.aigreentick.services.storage.config.properties.StorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class FileCleanupService {
    private final StorageProperties storageProperties;
    private final MeterRegistry meterRegistry;

        /**
     * Cleanup temporary files older than configured age.
     * Runs daily at 2 AM by default.
     */
    @Scheduled(cron = "${file-cleanup.cron:0 0 2 * * ?}")
    public void cleanupTempFiles() {
        log.info("Starting temporary file cleanup job");
        
        Counter successCounter = meterRegistry.counter("file.cleanup.success");
        Counter failureCounter = meterRegistry.counter("file.cleanup.failure");
        
        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        try {
            Path tempDir = Paths.get(storageProperties.getTempDir());
            
            if (!Files.exists(tempDir)) {
                log.warn("Temp directory does not exist: {}", tempDir);
                return;
            }

            Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);

            try (Stream<Path> files = Files.walk(tempDir)) {
                files.filter(Files::isRegularFile)
                     .forEach(file -> {
                         try {
                             Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                             if (lastModified.isBefore(cutoffTime)) {
                                 Files.delete(file);
                                 deletedCount.incrementAndGet();
                                 successCounter.increment();
                                 log.debug("Deleted temp file: {}", file);
                             }
                         } catch (IOException e) {
                             failedCount.incrementAndGet();
                             failureCounter.increment();
                             log.error("Failed to delete temp file: {}", file, e);
                         }
                     });
            }

            log.info("Temp file cleanup completed. Deleted: {}, Failed: {}", 
                     deletedCount.get(), failedCount.get());

        } catch (IOException e) {
            log.error("Error during temp file cleanup", e);
            failureCounter.increment();
        }
    }

    /**
     * Cleanup empty directories.
     */
    @Scheduled(cron = "${file-cleanup.directory-cleanup-cron:0 30 2 * * ?}")
    public void cleanupEmptyDirectories() {
        log.info("Starting empty directory cleanup");
        
        try {
            Path rootDir = Paths.get(storageProperties.getRoot());
            
            if (!Files.exists(rootDir)) {
                return;
            }

            try (Stream<Path> paths = Files.walk(rootDir)) {
                paths.filter(Files::isDirectory)
                     .filter(this::isEmptyDirectory)
                     .forEach(dir -> {
                         try {
                             Files.delete(dir);
                             log.debug("Deleted empty directory: {}", dir);
                         } catch (IOException e) {
                             log.error("Failed to delete empty directory: {}", dir, e);
                         }
                     });
            }

        } catch (IOException e) {
            log.error("Error during empty directory cleanup", e);
        }
    }

    private boolean isEmptyDirectory(Path path) {
        try (Stream<Path> entries = Files.list(path)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            log.error("Error checking if directory is empty: {}", path, e);
            return false;
        }
    }

}
