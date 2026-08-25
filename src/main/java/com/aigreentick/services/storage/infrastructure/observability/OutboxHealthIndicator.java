package com.aigreentick.services.storage.infrastructure.observability;

import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.config.properties.OutboxProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports DEGRADED — never DOWN — when dispatch falls behind. A backlog delays
 * side effects; it does not make the API unable to serve requests, and taking the
 * pod out of rotation would make the backlog worse.
 */
@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxPort outbox;
    private final OutboxProperties properties;

    public OutboxHealthIndicator(OutboxPort outbox, OutboxProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long lagSeconds = outbox.oldestPendingAgeSeconds();
        boolean degraded = lagSeconds > properties.lagAlertThreshold().toSeconds();
        return (degraded ? Health.status("DEGRADED") : Health.up())
                .withDetail("oldestPendingSeconds", lagSeconds)
                .withDetail("thresholdSeconds", properties.lagAlertThreshold().toSeconds())
                .build();
    }
}
