package com.aigreentick.services.storage.api.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Keyset page. BREAKING change from the predecessor's offset-based {@code Page<T>}:
 * offset degrades linearly with depth and the target is millions of files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {
}
