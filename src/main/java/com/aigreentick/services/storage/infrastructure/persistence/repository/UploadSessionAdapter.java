package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.UploadSessionPort;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.domain.upload.UploadSession;
import com.aigreentick.services.storage.domain.upload.UploadSessionId;
import com.aigreentick.services.storage.infrastructure.persistence.entity.UploadSessionEntity;
import com.aigreentick.services.storage.infrastructure.persistence.mapper.UploadSessionEntityMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UploadSessionAdapter implements UploadSessionPort {

    private final UploadSessionJpaRepository jpa;
    private final UploadSessionEntityMapper mapper;

    public UploadSessionAdapter(UploadSessionJpaRepository jpa, UploadSessionEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public UploadSession save(UploadSession session) {
        UploadSessionEntity entity = jpa.findById(session.id().value()).orElseGet(this::newEntity);
        mapper.apply(session, entity);
        return mapper.toDomain(jpa.save(entity));
    }

    private UploadSessionEntity newEntity() {
        try {
            Constructor<UploadSessionEntity> ctor = UploadSessionEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate UploadSessionEntity", e);
        }
    }

    @Override
    public Optional<UploadSession> findByIdForTenant(UploadSessionId id, TenantRef tenant) {
        return jpa.findByIdAndOrgIdAndProjectId(id.value(), tenant.orgId(), tenant.projectId())
                .map(mapper::toDomain);
    }

    @Override
    public List<UploadSession> findReclaimableForMaintenance(Instant now, int limit) {
        return jpa.findReclaimable(now, PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain).toList();
    }
}
