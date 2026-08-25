package com.aigreentick.services.storage.application.shared;

import com.aigreentick.services.storage.domain.media.MediaType;
import com.aigreentick.services.storage.domain.shared.TenantRef;

/**
 * Filter and pagination for listing.
 *
 * <p>KEYSET, not offset: {@code LIMIT n OFFSET m} degrades linearly as m grows,
 * and the target is millions of files per tenant.
 */
public record MediaListQuery(TenantRef tenant, MediaType mediaType, String cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public MediaListQuery {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }
}
