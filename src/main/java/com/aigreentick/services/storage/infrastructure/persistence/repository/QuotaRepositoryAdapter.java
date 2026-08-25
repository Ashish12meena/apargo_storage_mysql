package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.QuotaRepositoryPort;
import com.aigreentick.services.storage.domain.quota.Quota;
import com.aigreentick.services.storage.domain.quota.QuotaReservation;
import com.aigreentick.services.storage.domain.quota.QuotaScope;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.infrastructure.persistence.entity.OrgStorageEntity;
import com.aigreentick.services.storage.infrastructure.persistence.entity.ProjectStorageEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements {@link QuotaRepositoryPort} over JPA.
 *
 * <p>Reservation is ONE conditional statement per scope, issued as a
 * {@code @Modifying} bulk update. The invariant {@code used <= max} lives in the
 * WHERE clause, so the database enforces it and no row lock or retry loop is
 * needed. Rows-affected says whether we won.
 *
 * <p>Lock ordering is PROJECT then ORG, always, never reversed — that is what
 * prevents deadlock between concurrent uploads to different projects of one
 * organisation (ADR-003).
 */
@Repository
@Slf4j
public class QuotaRepositoryAdapter implements QuotaRepositoryPort {

    private final OrgStorageJpaRepository orgRepo;
    private final ProjectStorageJpaRepository projectRepo;

    public QuotaRepositoryAdapter(OrgStorageJpaRepository orgRepo,
                                  ProjectStorageJpaRepository projectRepo) {
        this.orgRepo = orgRepo;
        this.projectRepo = projectRepo;
    }

    /**
     * {@code MANDATORY}: reservation must join the caller's transaction so a
     * failure at the org step rolls back the project increment automatically.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaReservation reserve(TenantRef tenant, ByteSize amount) {
        // 1. PROJECT first, always.
        if (projectRepo.reserve(tenant.orgId(), tenant.projectId(), amount.value()) == 0) {
            return diagnose(QuotaScope.PROJECT, tenant, amount, findProjectQuota(tenant));
        }
        // 2. ORG second, always.
        if (orgRepo.reserve(tenant.orgId(), amount.value()) == 0) {
            // NO manual compensation for the project increment above. The caller
            // turns this result into an exception, which rolls back the enclosing
            // transaction and undoes it. The predecessor hand-wrote a compensating
            // decrement inside a transaction that was about to roll back anyway.
            return diagnose(QuotaScope.ORG, tenant, amount, findOrgQuota(tenant.orgId()));
        }
        return new QuotaReservation.Reserved(tenant, amount);
    }

    private QuotaReservation diagnose(QuotaScope scope, TenantRef tenant, ByteSize requested,
                                      Optional<Quota> quota) {
        return quota
                .<QuotaReservation>map(q -> new QuotaReservation.Exceeded(scope, requested, q.remaining()))
                .orElseGet(() -> new QuotaReservation.NotProvisioned(scope, tenant));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(TenantRef tenant, ByteSize amount) {
        projectRepo.release(tenant.orgId(), tenant.projectId(), amount.value());
        orgRepo.release(tenant.orgId(), amount.value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quota> findOrgQuota(long orgId) {
        return orgRepo.findById(orgId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quota> findProjectQuota(TenantRef tenant) {
        return projectRepo.findByOrgIdAndProjectId(tenant.orgId(), tenant.projectId())
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Quota upsertOrgQuota(long orgId, ByteSize max) {
        orgRepo.upsert(orgId, max.value());
        return findOrgQuota(orgId).orElseThrow(
                () -> new IllegalStateException("org quota missing after upsert: " + orgId));
    }

    @Override
    @Transactional
    public Quota upsertProjectQuota(TenantRef tenant, ByteSize max) {
        projectRepo.upsert(tenant.orgId(), tenant.projectId(), max.value());
        return findProjectQuota(tenant).orElseThrow(
                () -> new IllegalStateException("project quota missing after upsert: " + tenant));
    }

    @Override
    @Transactional
    public long correctUsage(TenantRef tenant, ByteSize actual) {
        int rows = projectRepo.correctUsage(tenant.orgId(), tenant.projectId(), actual.value());
        // Org usage is derived from its projects, so recompute rather than guess.
        orgRepo.recomputeUsageFromProjects(tenant.orgId());
        log.info("corrected quota usage for {} to {} bytes", tenant, actual.value());
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public long maxProjectLimit(long orgId) {
        return projectRepo.maxProjectLimit(orgId);
    }

    @Override
    @Transactional(readOnly = true)
    public long sumProjectLimitsExcluding(TenantRef tenant) {
        return projectRepo.sumLimitsExcluding(tenant.orgId(), tenant.projectId());
    }

    private Quota toDomain(OrgStorageEntity e) {
        return new Quota(QuotaScope.ORG, e.getOrgId(), null,
                ByteSize.of(e.getMaxBytes()), ByteSize.of(e.getUsedBytes()));
    }

    private Quota toDomain(ProjectStorageEntity e) {
        return new Quota(QuotaScope.PROJECT, e.getOrgId(), e.getProjectId(),
                ByteSize.of(e.getMaxBytes()), ByteSize.of(e.getUsedBytes()));
    }
}
