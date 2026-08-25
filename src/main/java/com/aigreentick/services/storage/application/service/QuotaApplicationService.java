package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.command.ProvisionQuotaCommand;
import com.aigreentick.services.storage.application.port.in.ManageQuotaUseCase;
import com.aigreentick.services.storage.application.port.out.AuditPort;
import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.port.out.QuotaRepositoryPort;
import com.aigreentick.services.storage.application.port.in.result.QuotaView;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.domain.exception.InvalidQuotaLimitException;
import com.aigreentick.services.storage.domain.exception.QuotaExceededException;
import com.aigreentick.services.storage.domain.exception.QuotaNotProvisionedException;
import com.aigreentick.services.storage.domain.quota.Quota;
import com.aigreentick.services.storage.domain.quota.QuotaReservation;
import com.aigreentick.services.storage.domain.quota.QuotaScope;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Quota provisioning, inspection, and reservation translation. */
@Service
public class QuotaApplicationService implements ManageQuotaUseCase {

    private final QuotaRepositoryPort quotaRepository;
    private final OutboxPort outbox;
    private final AuditPort audit;
    private final ClockPort clock;
    private final QuotaProperties properties;

    public QuotaApplicationService(QuotaRepositoryPort quotaRepository, OutboxPort outbox,
                                   AuditPort audit, ClockPort clock, QuotaProperties properties) {
        this.quotaRepository = quotaRepository;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Reserves, or throws the exception matching the failure mode.
     *
     * <p>{@code MANDATORY}: reservation must join the caller's transaction so that
     * the reservation and the PENDING media row commit together. If they did not,
     * a crash between them would strand quota with no session to sweep.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveOrThrow(TenantRef tenant, ByteSize amount) {
        QuotaReservation result = quotaRepository.reserve(tenant, amount);
        switch (result) {
            case QuotaReservation.Reserved ignored -> {
                // Reserved. Threshold events are emitted by the caller after commit.
            }
            case QuotaReservation.Exceeded e ->
                    throw new QuotaExceededException(e.scope(), e.requested().value(), e.available().value());
            case QuotaReservation.NotProvisioned n ->
                    throw new QuotaNotProvisionedException(
                            "quota not provisioned at scope=" + n.scope() + " for " + n.tenant());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(TenantRef tenant, ByteSize amount) {
        if (amount == null || amount.isZero()) {
            return;
        }
        quotaRepository.release(tenant, amount);
    }

    /**
     * Emits threshold events when utilisation crosses a configured boundary.
     * Called after the reservation commits, so the numbers reflect reality.
     */
    @Transactional
    public void emitThresholdEventsIfCrossed(TenantRef tenant) {
        quotaRepository.findProjectQuota(tenant).ifPresent(q -> emitIfCrossed(q, tenant.orgId(), tenant.projectId()));
        quotaRepository.findOrgQuota(tenant.orgId()).ifPresent(q -> emitIfCrossed(q, tenant.orgId(), null));
    }

    private void emitIfCrossed(Quota quota, long orgId, Long projectId) {
        int percent = quota.utilisationPercent();
        for (Integer threshold : properties.alertThresholdPercents()) {
            if (percent >= threshold) {
                outbox.append(new DomainEvent.QuotaThresholdCrossed(
                        orgId + (projectId == null ? "" : ":" + projectId),
                        orgId, projectId, threshold, quota.used().value(), quota.max().value(), clock.now()));
                break;
            }
        }
    }

    @Override
    @Transactional
    public QuotaView provision(ProvisionQuotaCommand command) {
        Quota quota;
        if (command.scope() == QuotaScope.ORG) {
            assertOrgLimitCoversProjects(command);
            quota = quotaRepository.upsertOrgQuota(command.orgId(), command.maxBytes());
        } else {
            TenantRef tenant = new TenantRef(command.orgId(), requireProjectId(command));
            assertProjectLimitFitsOrg(tenant, command.maxBytes());
            quota = quotaRepository.upsertProjectQuota(tenant, command.maxBytes());
        }

        audit.record(new TenantRef(command.orgId(), command.projectId() == null ? 1L : command.projectId()),
                command.actor(), AuditPort.AuditAction.QUOTA_PROVISIONED,
                command.scope() + ":" + command.orgId(),
                "{\"maxBytes\":" + command.maxBytes().value() + "}");
        return toView(quota);
    }


    // ── Hierarchy invariant: a project limit may never exceed its org total ──

    /**
     * A project cannot be granted more than the whole organisation owns. Rejecting
     * at provisioning time is what keeps the number an administrator sees meaningful:
     * without this, a project could show a 500 GB allowance inside a 100 GB org and
     * fail at upload with a confusing org-scope error instead.
     */
    private void assertProjectLimitFitsOrg(TenantRef tenant, ByteSize requested) {
        Quota org = quotaRepository.findOrgQuota(tenant.orgId())
                .orElseThrow(() -> new QuotaNotProvisionedException(
                        "org " + tenant.orgId() + " has no quota; provision it before any project"));

        if (requested.value() > org.max().value()) {
            throw new InvalidQuotaLimitException("project limit " + requested.value()
                    + " exceeds org total " + org.max().value() + " for " + tenant);
        }
        if (!properties.allowProjectOvercommit()) {
            long otherProjects = quotaRepository.sumProjectLimitsExcluding(tenant);
            if (otherProjects + requested.value() > org.max().value()) {
                throw new InvalidQuotaLimitException("project limits would sum to "
                        + (otherProjects + requested.value()) + ", over the org total "
                        + org.max().value());
            }
        }
    }

    /**
     * Lowering an org limit below a project it already contains is rejected rather
     * than silently clamping the projects. Clamping would shrink allowances an
     * administrator believes they granted, without telling anyone.
     */
    private void assertOrgLimitCoversProjects(ProvisionQuotaCommand command) {
        long largestProject = quotaRepository.maxProjectLimit(command.orgId());
        if (largestProject > command.maxBytes().value()) {
            throw new InvalidQuotaLimitException("org limit " + command.maxBytes().value()
                    + " is below an existing project limit of " + largestProject
                    + "; lower the project limits first");
        }
    }

    private long requireProjectId(ProvisionQuotaCommand command) {
        if (command.projectId() == null) {
            throw new IllegalArgumentException("projectId is required for PROJECT scope");
        }
        return command.projectId();
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaView getOrgQuota(long orgId) {
        return quotaRepository.findOrgQuota(orgId)
                .map(this::toView)
                .orElseThrow(() -> new QuotaNotProvisionedException("no org quota for orgId=" + orgId));
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaView getProjectQuota(TenantRef tenant) {
        return quotaRepository.findProjectQuota(tenant)
                .map(this::toView)
                .orElseThrow(() -> new QuotaNotProvisionedException("no project quota for " + tenant));
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaView getForTenant(TenantRef tenant) {
        return getProjectQuota(tenant);
    }

    private QuotaView toView(Quota quota) {
        return new QuotaView(quota.orgId(), quota.projectId(), quota.max().value(),
                quota.used().value(), quota.remaining().value(), quota.utilisation());
    }
}
