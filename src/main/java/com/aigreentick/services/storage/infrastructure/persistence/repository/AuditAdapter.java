package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.AuditPort;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.infrastructure.persistence.entity.MediaAuditEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Insert-only.
 *
 * <p>The predecessor could answer "how many bytes does org 7 use" but not "who
 * deleted this file" — {@code softDeleteById(mediaId, deletedBy)} accepted a
 * {@code deletedBy} argument and silently discarded it, because no column existed.
 */
@Repository
public class AuditAdapter implements AuditPort {

    private final MediaAuditJpaRepository jpa;

    public AuditAdapter(MediaAuditJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void record(TenantRef tenant, Actor actor, AuditAction action, String resourceId,
                       String detailJson) {
        MediaAuditEntity entity = new MediaAuditEntity();
        entity.setOrgId(tenant.orgId());
        entity.setProjectId(tenant.projectId());
        entity.setActorId(actor == null ? null : actor.userId());
        entity.setActorType(actor == null ? "SYSTEM" : actor.type().name());
        entity.setAction(action.name());
        entity.setResourceId(resourceId);
        entity.setDetail(detailJson);
        entity.setClientIp(actor == null ? null : actor.requestIp());
        entity.setTraceId(RequestContext.traceIdOrNull());
        entity.setOccurredAt(Instant.now());
        jpa.save(entity);
    }
}
