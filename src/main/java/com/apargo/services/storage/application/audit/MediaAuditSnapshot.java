package com.apargo.services.storage.application.audit;

import com.apargo.services.storage.domain.media.Media;
import com.apargo.services.storage.domain.media.MediaStatus;
import com.apargo.services.storage.domain.media.MediaType;
import com.apargo.services.storage.domain.media.ScanStatus;

/**
 * The audited fields of a {@link Media}, captured BEFORE it changes. Media is
 * updated in place, so without this the old values are gone by the time the
 * event is built.
 */
public record MediaAuditSnapshot(String mediaId, long orgId, long projectId, MediaStatus status,
                                 ScanStatus scanStatus, MediaType mediaType, long sizeBytes) {

    public static MediaAuditSnapshot of(Media media) {
        return new MediaAuditSnapshot(
                media.id() == null ? null : media.id().asString(),
                media.tenant().orgId(),
                media.tenant().projectId(),
                media.status(),
                media.scanStatus(),
                media.mediaType(),
                media.billableSize() == null ? 0L : media.billableSize().value());
    }
}
