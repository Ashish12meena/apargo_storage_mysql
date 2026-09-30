package com.apargo.services.storage.application.port.out;

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

    /**
     * Called once when {@code record} is dead-lettered: its retries are exhausted
     * and it will not be attempted again. Default: nothing. Must not throw; the
     * dispatcher guards the call anyway.
     *
     * @param attempts total attempts made, including the last one
     */
    default void onDeadLetter(OutboxPort.OutboxRecord record, int attempts, RuntimeException cause) {
    }
}
