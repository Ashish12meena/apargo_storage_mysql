package com.aigreentick.services.storage.domain.quota;

import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

/**
 * Outcome of a reservation attempt. Returned rather than thrown so the caller can
 * distinguish the failure modes: they map to different HTTP statuses (507 vs 400)
 * and different remedies.
 */
public sealed interface QuotaReservation {

    record Reserved(TenantRef tenant, ByteSize amount) implements QuotaReservation {
    }

    /** Quota exists but is insufficient. → 507. */
    record Exceeded(QuotaScope scope, ByteSize requested, ByteSize available) implements QuotaReservation {
    }

    /**
     * No quota row for this org/project. → 400. Distinct from {@link Exceeded}
     * because the remedy is admin provisioning, not deleting files.
     */
    record NotProvisioned(QuotaScope scope, TenantRef tenant) implements QuotaReservation {
    }

    default boolean isReserved() {
        return this instanceof Reserved;
    }
}
