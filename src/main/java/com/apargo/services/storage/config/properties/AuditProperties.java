package com.apargo.services.storage.config.properties;

import com.apargo.platform.contract.event.EventEnvironments;
import com.apargo.platform.contract.event.EventTopics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Audit event publishing ({@code audit.*}).
 *
 * <p>Every Java default equals the default in {@code application.yml}, so a
 * missing key behaves exactly like the documented value. The source service has
 * no Java default of its own: YAML supplies {@code spring.application.name}.
 *
 * @param enabled       false switches publishing off; APIs behave identically
 * @param sourceService value of {@code sourceService} on every event
 * @param environment   {@code development} / {@code staging} / {@code production}
 */
@Validated
@ConfigurationProperties(prefix = "audit")
public record AuditProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue(AuditProperties.DEFAULT_SOURCE_SERVICE) @NotBlank String sourceService,
        @DefaultValue(EventEnvironments.DEVELOPMENT) @NotBlank String environment,
        @DefaultValue @Valid @NotNull Topics topics,
        @DefaultValue @Valid @NotNull Publisher publisher) {

    /** Same value as {@code spring.application.name}. */
    public static final String DEFAULT_SOURCE_SERVICE = "storage-service";

    public AuditProperties {
        if (!EventEnvironments.isValid(environment)) {
            throw new IllegalArgumentException("audit.environment must be one of "
                    + EventEnvironments.ALL + " but was '" + environment + "'");
        }
    }

    /** @param audit topic audit events are published to */
    public record Topics(@DefaultValue(EventTopics.DEFAULT_AUDIT_TOPIC) @NotBlank String audit) {
    }

    /**
     * The dedicated send pool ({@code auditPublisherExecutor}).
     *
     * @param poolSize                threads sending to Kafka
     * @param queueCapacity           events waiting for a thread; beyond this an event is logged and dropped
     * @param awaitTerminationSeconds how long shutdown waits for queued events
     * @param threadNamePrefix        thread name prefix, visible in thread dumps and logs
     */
    public record Publisher(
            @DefaultValue("2") @Min(1) int poolSize,
            @DefaultValue("10000") @Min(1) int queueCapacity,
            @DefaultValue("15") @Min(0) int awaitTerminationSeconds,
            @DefaultValue("audit-publisher-") @NotBlank String threadNamePrefix) {
    }
}
