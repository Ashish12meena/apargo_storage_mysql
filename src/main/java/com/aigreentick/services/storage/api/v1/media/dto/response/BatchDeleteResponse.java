package com.aigreentick.services.storage.api.v1.media.dto.response;

import java.util.List;

/**
 * {@code data} of {@code DELETE /api/v1/media/batch}: per-id outcomes in request
 * order. An object rather than a bare array, because the API Standard never
 * allows {@code data} to be an array; same summary fields as the batch upload.
 */
public record BatchDeleteResponse(int successCount, int failedCount, List<BatchItemResult> results) {

    public static BatchDeleteResponse of(List<BatchItemResult> results) {
        int ok = (int) results.stream().filter(BatchItemResult::success).count();
        return new BatchDeleteResponse(ok, results.size() - ok, List.copyOf(results));
    }
}
