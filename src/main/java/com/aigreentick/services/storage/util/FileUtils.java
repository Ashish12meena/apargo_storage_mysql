package com.aigreentick.services.storage.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.storage.dto.file.FileDetailsDto;
import com.aigreentick.services.storage.exception.FileStorageException;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for file operations with proper error handling.
 */
@Slf4j
public class FileUtils {

    private FileUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Get file details from a File object.
     * 
     * @param file the file to analyze
     * @return FileDetailsDto containing file metadata
     * @throws IllegalArgumentException if file is invalid
     */
    public static FileDetailsDto getFileDetails(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("The provided File is null, does not exist, or is not a valid file.");
        }

        String filename = file.getName();
        long fileSize = file.length();
        String mimeType = determineMimeType(file);

        return FileDetailsDto.builder()
                .fileName(filename)
                .mimeType(mimeType)
                .fileSize(fileSize)
                .file(file)
                .build();
    }

    /**
     * Convert MultipartFile to temporary File.
     * 
     * @param multipartFile the multipart file to convert
     * @return temporary File object
     * @throws FileStorageException if conversion fails
     */
    public static File convertMultipartToFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("MultipartFile is null or empty.");
        }

        try {
            String originalFilename = multipartFile.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "upload";
            }

            // Sanitize filename
            originalFilename = sanitizeFilename(originalFilename);

            // Split prefix and suffix
            String prefix;
            String suffix;
            int dotIndex = originalFilename.lastIndexOf('.');

            if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
                prefix = originalFilename.substring(0, dotIndex);
                suffix = originalFilename.substring(dotIndex);
            } else {
                prefix = originalFilename;
                suffix = null;
            }

            // Ensure prefix is at least 3 chars
            if (prefix.length() < 3) {
                prefix = prefix + "___";
            } else if (prefix.length() > 50) {
                prefix = prefix.substring(0, 50);
            }

            // Create temp file
            File tempFile = File.createTempFile(prefix + "_", suffix);
            tempFile.deleteOnExit();

            // Transfer content
            multipartFile.transferTo(tempFile);

            log.debug("Converted MultipartFile to temp file: {}", tempFile.getAbsolutePath());
            return tempFile;

        } catch (IOException ex) {
            log.error("Failed to convert MultipartFile to File", ex);
            throw new FileStorageException("Failed to convert MultipartFile to File", ex);
        }
    }

    /**
     * Delete a file safely without throwing exceptions.
     * 
     * @param file the file to delete
     */
    public static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }

        try {
            Path path = file.toPath();
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Successfully deleted file: {}", file.getAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("Failed to delete file: {}. Reason: {}", file.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Sanitize filename to prevent security issues.
     * 
     * @param filename the filename to sanitize
     * @return sanitized filename
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "file";
        }

        // Remove path traversal attempts
        filename = filename.replaceAll("\\.\\./", "");
        filename = filename.replaceAll("\\.\\\\", "");

        // Replace invalid characters with underscore
        filename = filename.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

        return filename;
    }

    /**
     * Determine MIME type of a file.
     * 
     * @param file the file to analyze
     * @return MIME type string
     */
    private static String determineMimeType(File file) {
        try {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType != null && !mimeType.isEmpty()) {
                return mimeType;
            }
        } catch (IOException e) {
            log.warn("Could not determine MIME type for file: {}. Error: {}",
                    file.getName(), e.getMessage());
        }

        return "application/octet-stream";
    }

    /**
     * Validate file path to prevent directory traversal attacks.
     * 
     * @param basePath the base directory path
     * @param filePath the file path to validate
     * @return true if path is safe
     */
    public static boolean isPathSafe(Path basePath, Path filePath) {
        try {
            Path normalizedBase = basePath.toRealPath();
            Path normalizedFile = filePath.normalize();
            return normalizedFile.startsWith(normalizedBase);
        } catch (IOException e) {
            log.error("Error validating path safety", e);
            return false;

        }
    }
}
