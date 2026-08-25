package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code outbox.*} — dispatcher tuning. */
@Validated
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10") int maxAttempts,
        @DefaultValue("PT2S") Duration baseBackoff,
        @DefaultValue("PT5M") Duration maxBackoff,
        @DefaultValue("PT5M") Duration lagAlertThreshold,
        @DefaultValue("P7D") Duration retentionAfterDispatch) {
}
