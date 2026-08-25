package com.aigreentick.services.storage.application.shared;

import java.util.List;
import java.util.function.Function;

/**
 * Keyset page. No total count: producing one requires a {@code COUNT(*)} over the
 * tenant's whole filtered set on every page request, which is the most expensive
 * part of a listing call and is almost never used.
 */
public record PageView<T>(List<T> items, String nextCursor, boolean hasMore) {

    public <R> PageView<R> map(Function<T, R> mapper) {
        return new PageView<>(items.stream().map(mapper).toList(), nextCursor, hasMore);
    }

    public static <T> PageView<T> empty() {
        return new PageView<>(List.of(), null, false);
    }
}
