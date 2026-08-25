package com.aigreentick.services.storage.application.port.out;

/**
 * Dispatch target for a single outbox event type.
 *
 * <p>Contract: idempotent, bounded in time, and never able to fail the request
 * that produced the event. Throwing schedules a retry; returning normally
 * acknowledges.
 */
public interface EventPublisherPort {

    String eventType();

    void handle(OutboxPort.OutboxRecord record);
}
