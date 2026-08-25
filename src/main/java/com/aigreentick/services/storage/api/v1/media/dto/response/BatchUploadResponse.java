package com.aigreentick.services.storage.api.v1.media.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Wire shape for a batch upload, carried inside the standard {@code ApiResponse}
 * envelope as {@code data}.
 *
 * <p>{@code results} is in REQUEST ORDER with exactly one entry per submitted
 * file, so {@code results.size() == successCount + failedCount} always holds. The
 * client matches entries to its own tasks by position, since two files in one
 * batch may share a filename.
 *
 * <p>Field names serialise as camelCase under Jackson's default naming strategy;
 * no strategy is configured for this service and none is needed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchUploadResponse(
        int successCount,
        int failedCount,
        List<Item> results) {

    /**
     * One submitted file's outcome, FLATTENED.
     *
     * <p>Deliberately NOT the nested {@code MediaResponse} that single-file upload
     * returns. {@code template-service} consumes this endpoint by reading four
     * fields per entry and does not want to walk into a sub-object for them; a
     * flat entry is also what makes {@code error} sit at the same level as the
     * success fields, so a client can branch on one field without a null-check on
     * a parent object first.
     *
     * <p>Note this record does NOT carry {@code @JsonInclude(NON_NULL)}. That is
     * intentional and is the one place in this API where nulls are emitted: every
     * entry always shows all seven keys, so {@code "error": null} is present on a
     * success and {@code "url": null} is present on a failure. A client can then
     * read a fixed shape rather than testing for key presence.
     *
     * @param originalFilename the filename supplied in the multipart part,
     *                         verbatim — never a storage key, temp-file name, or
     *                         normalised variant
     * @param status           {@code SUCCESS}, {@code FAILED}, or {@code SKIPPED}
     * @param error            null on success; on failure a stable code plus a
     *                         CLIENT-SAFE message, never an internal exception
     *                         message, storage key, path, or provider error
     */
    public record Item(
            String originalFilename,
            String status,
            String url,
            String mediaType,
            String contentType,
            Long fileSizeBytes,
            Error error) {

        /** @param code a stable {@code ErrorCode} name — clients branch on this */
        public record Error(String code, String message) {
        }

        public static Item success(String originalFilename, String url, String mediaType,
                                   String contentType, long fileSizeBytes) {
            return new Item(originalFilename, "SUCCESS", url, mediaType, contentType,
                    fileSizeBytes, null);
        }

        public static Item failure(String originalFilename, String status,
                                   String code, String message) {
            return new Item(originalFilename, status, null, null, null, null,
                    new Error(code, message));
        }
    }
}