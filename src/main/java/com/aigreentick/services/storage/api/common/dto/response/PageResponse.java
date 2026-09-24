package com.aigreentick.services.storage.api.common.dto.response;

import java.util.List;

/**
 * {@code data} of a list endpoint using CURSOR paging (API Standard §5,
 * "Cursor pagination"): {@code {items, pagination: {size, nextCursor, hasNext}}}.
 *
 * <p>Cursor, not page numbers: offset paging degrades linearly with depth and
 * the target is millions of files, and a total count would need a
 * {@code COUNT(*)} over the tenant's whole set on every request. The cursor is
 * opaque; clients must not decode or build it.
 */
public record PageResponse<T>(List<T> items, CursorPagination pagination) {

    /**
     * @param size       page size used
     * @param nextCursor send as {@code cursor} for the next page; null on the last page
     * @param hasNext    false on the last page
     */
    public record CursorPagination(int size, String nextCursor, boolean hasNext) {
    }
}
