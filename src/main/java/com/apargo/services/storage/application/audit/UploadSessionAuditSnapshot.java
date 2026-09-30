package com.apargo.services.storage.application.audit;

import com.apargo.services.storage.domain.upload.UploadMode;
import com.apargo.services.storage.domain.upload.UploadSession;
import com.apargo.services.storage.domain.upload.UploadSessionStatus;

/** The audited fields of an {@link UploadSession}, captured BEFORE it changes. */
public record UploadSessionAuditSnapshot(String sessionId, long orgId, long projectId,
                                         UploadSessionStatus status, UploadMode mode,
                                         long declaredSizeBytes, String mediaId) {

    public static UploadSessionAuditSnapshot of(UploadSession session) {
        return new UploadSessionAuditSnapshot(
                session.id().value(),
                session.tenant().orgId(),
                session.tenant().projectId(),
                session.status(),
                session.mode(),
                session.declaredSize() == null ? 0L : session.declaredSize().value(),
                session.mediaId() == null ? null : session.mediaId().asString());
    }
}
