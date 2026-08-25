package com.aigreentick.services.storage.infrastructure.persistence.mapper;

import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.domain.upload.UploadMode;
import com.aigreentick.services.storage.domain.upload.UploadSession;
import com.aigreentick.services.storage.domain.upload.UploadSessionId;
import com.aigreentick.services.storage.domain.upload.UploadSessionStatus;
import com.aigreentick.services.storage.infrastructure.persistence.entity.UploadSessionEntity;
import org.springframework.stereotype.Component;

@Component
public class UploadSessionEntityMapper {

    public UploadSession toDomain(UploadSessionEntity e) {
        return UploadSession.rehydrate(
                UploadSessionId.of(e.getId()),
                new TenantRef(e.getOrgId(), e.getProjectId()),
                new StorageKey(e.getStorageKey()),
                UploadMode.valueOf(e.getMode()),
                ByteSize.of(e.getDeclaredSize()),
                UploadSessionStatus.valueOf(e.getStatus()),
                e.getMediaId() == null ? null : MediaId.of(e.getMediaId()),
                e.getIdempotencyKey(),
                e.getProviderUploadId(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getCompletedAt());
    }

    public void apply(UploadSession session, UploadSessionEntity e) {
        e.setId(session.id().value());
        e.setOrgId(session.tenant().orgId());
        e.setProjectId(session.tenant().projectId());
        e.setMediaId(session.mediaId() == null ? null : session.mediaId().value());
        e.setStorageKey(session.storageKey().value());
        e.setMode(session.mode().name());
        e.setDeclaredSize(session.declaredSize().value());
        e.setStatus(session.status().name());
        e.setIdempotencyKey(session.idempotencyKey());
        e.setProviderUploadId(session.providerUploadId());
        e.setCreatedAt(session.createdAt());
        e.setExpiresAt(session.expiresAt());
        e.setCompletedAt(session.completedAt());
    }
}
