package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * {@code quota.*}
 *
 * @param uploadSessionTtl  how long a reservation survives without a commit. Too
 *                          short breaks slow uploads; too long lets abandoned
 *                          uploads hold quota.
 * @param deleteGracePeriod DELETED → PURGED delay, so an accidental delete is
 *                          recoverable.
 */
@Validated
@ConfigurationProperties(prefix = "quota")
public record QuotaProperties(
        @DefaultValue("PT30M") Duration uploadSessionTtl,
        @DefaultValue("P7D") Duration deleteGracePeriod,
        @DefaultValue("80,95") List<Integer> alertThresholdPercents,
        @DefaultValue("true") boolean reconciliationEnabled,
        /**
         * When true (the default), the SUM of project limits may exceed the org
         * limit. Each individual project is still capped at the org total, and the
         * org row is still checked on every reservation, so the real bound holds
         * regardless — this only controls whether an administrator may hand out
         * optimistic per-project allowances.
         *
         * <p>Set false to require that project limits sum to no more than the org
         * limit, which makes each project's number a guarantee rather than a cap.
         */
        @DefaultValue("true") boolean allowProjectOvercommit,
        /**
         * Rows marked per teardown pass. Larger finishes sooner but holds locks
         * longer; smaller interleaves better with live traffic.
         */
        @DefaultValue("500") int teardownBatchSize) {

    public QuotaProperties {
        alertThresholdPercents = alertThresholdPercents == null
                ? List.of(80, 95)
                : alertThresholdPercents.stream().sorted((a, b) -> Integer.compare(b, a)).toList();
    }
}
