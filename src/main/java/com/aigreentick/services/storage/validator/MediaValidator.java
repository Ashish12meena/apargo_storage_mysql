package com.aigreentick.services.storage.validator;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.storage.config.properties.MediaProperties;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.exception.InvalidMediaException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MediaValidator {
    private final MediaProperties properties;

    /**
     * Validates if the uploaded file meets all requirements.
     *
     * @param file the multipart file to validate
     * @throws InvalidMediaException if validation fails
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("File is empty or null");
        }

        validateFileSize(file);
        validateContentType(file);
        validateFilename(file);
    }

    /**
     * Validates the file size.
     */
    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > properties.getUploadMaxSize()) {
            throw new InvalidMediaException(
                    String.format("File size exceeds maximum allowed size of %d bytes", properties.getUploadMaxSize()));
        }
    }

    /**
     * Validates the content type (MIME type).
     */
    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null || contentType.isEmpty()) {
            throw new InvalidMediaException("Content type is missing");
        }

        List<String> allAllowedTypes = Arrays.asList(
                properties.getUploadAllowedImageTypes(),
                properties.getUploadAllowedVideoTypes(),
                properties.getUploadAllowedDocumentTypes(),
                properties.getUploadAllowedAudioTypes()).stream()
                .flatMap(List::stream)
                .toList();

        if (!allAllowedTypes.contains(contentType)) {
            throw new InvalidMediaException(
                    String.format("Content type '%s' is not allowed", contentType));
        }
    }

    /**
     * Validates the filename for security concerns.
     */
    private void validateFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();

        if (filename == null || filename.isEmpty()) {
            throw new InvalidMediaException("Filename is missing");
        }

        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new InvalidMediaException("Invalid filename: path traversal detected");
        }

        // Check for valid extension
        if (!filename.contains(".")) {
            throw new InvalidMediaException("File must have an extension");
        }
    }

    /**
     * Determines the media type based on content type.
     */
    public MediaType determineMediaType(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("Unsupported media type for WhatsApp: " + contentType);
        }

        if (contentType.startsWith("image/")) {
            return MediaType.IMAGE;
        } else if (contentType.startsWith("video/")) {
            return MediaType.VIDEO;
        } else if (contentType.startsWith("audio/")) {
            return MediaType.AUDIO;
        } else if (contentType.contains("pdf") || contentType.contains("document") || contentType.contains("text")) {
            return MediaType.DOCUMENT;
        }

        throw new IllegalArgumentException("Unsupported media type for WhatsApp: " + contentType);
    }

    public MediaType detectMediaType(String mime) {
        if (mime == null)
            throw new IllegalArgumentException("Content type is missing.");

        if (properties.getUploadAllowedImageTypes().contains(mime))
            return MediaType.IMAGE;
        if (properties.getUploadAllowedVideoTypes().contains(mime))
            return MediaType.VIDEO;
        if (properties.getUploadAllowedAudioTypes().contains(mime))
            return MediaType.AUDIO;
        if (properties.getUploadAllowedDocumentTypes().contains(mime))
            return MediaType.DOCUMENT;

        throw new IllegalArgumentException("Unsupported media type for WhatsApp: " + mime);
    }

}
