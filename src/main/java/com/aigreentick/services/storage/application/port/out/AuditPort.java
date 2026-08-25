package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;

/**
 * Append-only record of who did what.
 *
 * <p>The predecessor could answer "how many bytes does org 7 use" but not "who
 * deleted this file" — there was no per-user identity anywhere, and
 * {@code softDeleteById(mediaId, deletedBy)} accepted a {@code deletedBy} argument
 * and silently discarded it, because no such column existed.
 */
public interface AuditPort {

    void record(TenantRef tenant, Actor actor, AuditAction action, String resourceId, String detailJson);

    enum AuditAction {
        UPLOAD_INITIATED,
        UPLOAD_COMPLETED,
        UPLOAD_ABORTED,
        MEDIA_DOWNLOADED,
        MEDIA_DELETED,
        MEDIA_RESTORED,
        MEDIA_PURGED,
        MEDIA_QUARANTINED,
        QUOTA_PROVISIONED,
        QUOTA_RECONCILED
    }
}
