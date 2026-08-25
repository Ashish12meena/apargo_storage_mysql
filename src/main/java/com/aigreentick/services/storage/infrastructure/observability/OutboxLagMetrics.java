package com.aigreentick.services.storage.infrastructure.observability;

import com.aigreentick.services.storage.application.port.out.OutboxPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Publishes {@code storage.outbox.lag.seconds}, refreshed by the scheduler. */
@Component
public class OutboxLagMetrics {

    private final OutboxPort outbox;
    private final AtomicLong lagSeconds = new AtomicLong(0);

    public OutboxLagMetrics(OutboxPort outbox, MeterRegistry meters) {
        this.outbox = outbox;
        meters.gauge("storage.outbox.lag.seconds", lagSeconds, AtomicLong::doubleValue);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000)
    public void refresh() {
        try {
            lagSeconds.set(outbox.oldestPendingAgeSeconds());
        } catch (RuntimeException e) {
            // A metrics refresh must never disturb the application.
            lagSeconds.set(-1);
        }
    }
}
