package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.domain.quota.Quota;
import com.aigreentick.services.storage.domain.quota.QuotaReservation;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.util.Optional;

/**
 * Quota persistence and — importantly — quota ARITHMETIC.
 *
 * <p>Reservation is one port operation rather than read-modify-write in a service,
 * because the safety property depends on it being a single atomic statement. The
 * database enforces the invariant: no row lock (which serialises a tenant), no
 * optimistic-retry loop (which degrades under exactly the contention it exists to
 * handle). See ADR-003.
 */
public interface QuotaRepositoryPort {

    /** Atomic and all-or-nothing across both scopes. Locks PROJECT then ORG. */
    QuotaReservation reserve(TenantRef tenant, ByteSize amount);

    /** Idempotent-safe release. Floors at zero rather than going negative. */
    void release(TenantRef tenant, ByteSize amount);

    Optional<Quota> findOrgQuota(long orgId);

    Optional<Quota> findProjectQuota(TenantRef tenant);

    Quota upsertOrgQuota(long orgId, ByteSize max);

    Quota upsertProjectQuota(TenantRef tenant, ByteSize max);

    /** Reconciliation: overwrite consumption with a recomputed truth. */
    long correctUsage(TenantRef tenant, ByteSize actual);

    /** Largest project limit in an org. Guards a downward org-limit change. */
    long maxProjectLimit(long orgId);

    /** Sum of project limits excluding one project. Used only when overcommit is off. */
    long sumProjectLimitsExcluding(TenantRef tenant);
}
