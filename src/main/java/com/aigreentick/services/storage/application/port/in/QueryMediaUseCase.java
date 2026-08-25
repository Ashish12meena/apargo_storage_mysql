package com.aigreentick.services.storage.application.port.in;

import com.aigreentick.services.storage.application.shared.MediaListQuery;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.application.shared.PageView;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.time.Duration;

/**
 * Read side. Every method takes an explicit {@link TenantRef}; there is no
 * overload that omits it, so a tenant-blind read cannot be written by accident.
 */
public interface QueryMediaUseCase {

    MediaView getById(MediaId id, TenantRef tenant);

    PageView<MediaView> list(MediaListQuery query);

    /**
     * A short-lived, tenant-scoped read URL.
     *
     * <p>Replaces {@code GET /media/public-url?storageKey=...}, which accepted a
     * client-supplied key and performed no ownership check.
     */
    String generateDownloadUrl(MediaId id, TenantRef tenant, Duration ttl);
}
