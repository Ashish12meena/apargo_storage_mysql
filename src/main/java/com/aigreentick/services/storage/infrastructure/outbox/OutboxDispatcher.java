package com.aigreentick.services.storage.infrastructure.outbox;

import com.aigreentick.services.storage.application.port.out.EventPublisherPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.config.properties.OutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls the outbox and hands events to registered handlers.
 *
 * <p>At-least-once delivery, so every handler must be idempotent. After the
 * attempt limit a row moves to {@code FAILED} and alerts — a real dead-letter
 * path, which the predecessor's fire-and-forget WhatsApp push did not have: a
 * failed push was logged, swallowed, and afterwards undiscoverable.
 *
 * <p>Runs on every replica; {@code FOR UPDATE SKIP LOCKED} makes that useful
 * rather than merely safe.
 */
@Component
@Slf4j
public class OutboxDispatcher {

    private final OutboxPort outbox;
    private final Map<String, EventPublisherPort> handlers;
    private final OutboxProperties properties;
    private final MeterRegistry meters;

    public OutboxDispatcher(OutboxPort outbox, List<EventPublisherPort> handlerList,
                            OutboxProperties properties, MeterRegistry meters) {
        this.outbox = outbox;
        this.handlers = handlerList.stream().collect(
                Collectors.toMap(EventPublisherPort::eventType, Function.identity()));
        this.properties = properties;
        this.meters = meters;
        log.info("outbox dispatcher registered {} handler(s): {}", handlers.size(), handlers.keySet());
    }

    public int dispatchOnce() {
        if (!properties.enabled()) {
            return 0;
        }
        List<OutboxPort.OutboxRecord> batch = outbox.claimBatch(properties.batchSize());
        int dispatched = 0;
        for (OutboxPort.OutboxRecord record : batch) {
            EventPublisherPort handler = handlers.get(record.eventType());
            if (handler == null) {
                // No consumer is a legitimate state, not a failure: the event is
                // recorded for future subscribers and acknowledged now.
                outbox.markDispatched(record.id());
                continue;
            }
            try {
                handler.handle(record);
                outbox.markDispatched(record.id());
                dispatched++;
            } catch (RuntimeException e) {
                recordFailure(record, e);
            }
        }
        return dispatched;
    }

    private void recordFailure(OutboxPort.OutboxRecord record, RuntimeException e) {
        int attempts = record.attempts() + 1;
        boolean deadLetter = attempts >= properties.maxAttempts();
        Instant nextRetry = Instant.now().plus(backoff(attempts));

        outbox.markFailed(record.id(), e.toString(), nextRetry, deadLetter);

        if (deadLetter) {
            meters.counter("storage.outbox.failed", "event_type", record.eventType()).increment();
            log.error("outbox event {} DEAD-LETTERED after {} attempts: {}",
                    record.id(), attempts, e.toString(), e);
        } else {
            log.warn("outbox event {} failed (attempt {}/{}), retrying at {}: {}",
                    record.id(), attempts, properties.maxAttempts(), nextRetry, e.toString());
        }
    }

    /** Exponential with jitter, so retries from many replicas do not synchronise. */
    private Duration backoff(int attempts) {
        long baseMillis = properties.baseBackoff().toMillis();
        long capped = Math.min(properties.maxBackoff().toMillis(),
                baseMillis * (long) Math.pow(2, Math.min(attempts, 16)));
        long jitter = ThreadLocalRandom.current().nextLong(capped / 4 + 1);
        return Duration.ofMillis(capped - jitter);
    }
}
