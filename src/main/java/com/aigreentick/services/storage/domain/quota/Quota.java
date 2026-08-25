package com.aigreentick.services.storage.domain.quota;

import com.aigreentick.services.storage.domain.shared.ByteSize;

/**
 * A storage allowance and its consumption at one scope.
 *
 * <p>The invariant {@code used <= max} is enforced in the DATABASE by a
 * conditional UPDATE, not here. This type serves the read side, the reconciliation
 * job, and threshold alerting. It is deliberately NOT the mechanism that makes
 * concurrent reservation safe (ADR-003).
 */
public record Quota(QuotaScope scope, long orgId, Long projectId, ByteSize max, ByteSize used) {

    public ByteSize remaining() {
        return max.value() <= used.value() ? ByteSize.ZERO : max.minus(used);
    }

    /** Advisory only — for pre-flight rejection and threshold events. */
    public boolean hasCapacityFor(ByteSize size) {
        return used.value() + size.value() <= max.value();
    }

    /** 0.0–1.0. Drives {@code storage.quota.utilisation} and threshold events. */
    public double utilisation() {
        return max.value() == 0 ? 0.0 : (double) used.value() / (double) max.value();
    }

    public int utilisationPercent() {
        return (int) Math.floor(utilisation() * 100);
    }
}
