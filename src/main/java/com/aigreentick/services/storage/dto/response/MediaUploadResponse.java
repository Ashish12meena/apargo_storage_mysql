package com.aigreentick.services.storage.dto.response;

import java.time.Instant;

import com.aigreentick.services.storage.enums.MediaType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for media upload operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaUploadResponse {
     /**
     * Publicly accessible URL of the uploaded media.
     */
    private String url;

    /**
     * Original filename provided during upload.
     */
    private String originalFilename;

    /**
     * System-generated unique filename.
     */
    private String storedFilename;

    /**
     * Media type (e.g., IMAGE, VIDEO, DOCUMENT, AUDIO).
     */
    private MediaType mediaType;

    /**
     * Content type (MIME type) of the file.
     */
    private String contentType;

    /**
     * File size in bytes.
     */
    private Long fileSizeBytes;

    /*
     * mediaId if uploaded on facebook
     */
    private String mediaId;

    /**
     * Timestamp when the file was uploaded.
     */
    private Instant uploadedAt;

}
