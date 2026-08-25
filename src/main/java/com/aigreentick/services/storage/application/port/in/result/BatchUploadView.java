package com.aigreentick.services.storage.application.port.in.result;

import java.util.List;

/**
 * Outcome of a batch upload.
 *
 * <p>{@code results} is in REQUEST ORDER and always holds exactly one entry per
 * submitted file, so {@code results.size() == successCount + failedCount}.
 */
public record BatchUploadView(int successCount, int failedCount, List<ItemView> results) {

    public BatchUploadView {
        results = results == null ? List.of() : List.copyOf(results);
    }

    /** Per-file outcome. SKIPPED counts towards the failure count. */
    public enum Status {
        SUCCESS,
        FAILED,
        /** Not attempted: the storage backend was failing repeatedly. */
        SKIPPED
    }

    /**
     * @param media        the same read model single-file upload returns, on success only
     * @param errorCode    stable {@code ErrorCode} name, on failure only
     * @param errorMessage the CLIENT-facing message; never an internal exception
     *                     message, storage key, path, or provider error
     */
    public record ItemView(
            String originalFilename,
            Status status,
            MediaView media,
            String errorCode,
            String errorMessage) {

        public static ItemView success(String filename, MediaView media) {
            return new ItemView(filename, Status.SUCCESS, media, null, null);
        }

        public static ItemView failed(String filename, String errorCode, String errorMessage) {
            return new ItemView(filename, Status.FAILED, null, errorCode, errorMessage);
        }

        public static ItemView skipped(String filename, String errorCode, String errorMessage) {
            return new ItemView(filename, Status.SKIPPED, null, errorCode, errorMessage);
        }
    }
}
