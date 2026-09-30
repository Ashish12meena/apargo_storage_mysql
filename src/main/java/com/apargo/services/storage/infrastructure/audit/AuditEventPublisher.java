package com.apargo.services.storage.infrastructure.audit;

import com.apargo.platform.contract.audit.AuditEventDto;
import com.apargo.platform.contract.audit.AuditEventValidator;
import com.apargo.platform.contract.event.EventTopics;
import com.apargo.services.storage.common.context.TraceContext;
import com.apargo.services.storage.config.AuditPublisherConfig;
import com.apargo.services.storage.config.properties.AuditProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends audit events to Kafka. The ONLY class in the service that touches Kafka.
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li><b>Only committed changes.</b> {@code AFTER_COMMIT}: an event raised in a
 *       transaction that rolls back is discarded by Spring and never reaches here.
 *       {@code fallbackExecution = true} publishes events raised outside a
 *       transaction (each write committed on its own) immediately.</li>
 *   <li><b>No request waits for Kafka.</b> This method serializes, validates and
 *       hands off; the send runs on {@code auditPublisherExecutor}.
 *       {@code KafkaOperations#send} is itself non-blocking; its result is only
 *       logged.</li>
 *   <li><b>Nothing escapes.</b> An exception thrown from an after-commit callback
 *       would reach the business caller after its transaction already committed.
 *       Every failure — invalid event, full queue, serialization or send error —
 *       is logged at ERROR with the event id and the full event JSON, so it can
 *       be replayed with the same key. Delivery problems never change an event's
 *       status.</li>
 * </ul>
 *
 * <p>Delivery is best effort: the producer retries until
 * {@code delivery.timeout.ms}; duplicates are possible and the audit service
 * de-duplicates on {@code eventId}. With {@code audit.enabled=false} events are
 * logged at DEBUG and not sent.
 */
@Component
@Slf4j
public class AuditEventPublisher {

    private final KafkaOperations<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final AuditProperties properties;
    private final Executor executor;

    public AuditEventPublisher(KafkaOperations<String, String> kafka, ObjectMapper objectMapper,
                               AuditProperties properties,
                               @Qualifier(AuditPublisherConfig.AUDIT_PUBLISHER_EXECUTOR) Executor executor) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEventDto event) {
        if (event == null) {
            log.error("Audit event not published: null event");
            return;
        }
        String json = null;
        try {
            json = objectMapper.writeValueAsString(event);

            List<String> problems = AuditEventValidator.validate(event);
            if (!problems.isEmpty()) {
                log.error("Audit event not published: invalid. eventId={} eventType={} problems={} event={}",
                        event.eventId(), event.eventType(), problems, json);
                return;
            }
            if (!properties.enabled()) {
                log.debug("Audit disabled (audit.enabled=false); event not published: {}", json);
                return;
            }

            String payload = json;
            executor.execute(() -> send(event, payload));

        } catch (RejectedExecutionException ex) {
            log.error("Audit event not published: publisher queue full (capacity {}). eventId={} event={}",
                    properties.publisher().queueCapacity(), event.eventId(), json);
        } catch (JsonProcessingException ex) {
            log.error("Audit event not published: could not serialize. eventId={} eventType={} event={}",
                    event.eventId(), event.eventType(), event, ex);
        } catch (RuntimeException ex) {
            log.error("Audit event not published: unexpected error. eventId={} event={}",
                    event.eventId(), json != null ? json : event, ex);
        }
    }

    /** Runs on the audit pool. Never throws: the pool thread has no caller to report to. */
    void send(AuditEventDto event, String json) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    properties.topics().audit(), null, event.eventId(), json, headers(event));

            kafka.send(record).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.error("Audit event not delivered to Kafka. eventId={} topic={} event={}",
                            event.eventId(), record.topic(), json, failure);
                } else if (log.isDebugEnabled()) {
                    log.debug("Audit event delivered. eventId={} eventType={} partition={} offset={}",
                            event.eventId(), event.eventType(),
                            result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException ex) {
            // send() itself can throw: metadata wait beyond max.block.ms, a closed
            // producer during shutdown, a serializer error.
            log.error("Audit event not sent to Kafka. eventId={} event={}", event.eventId(), json, ex);
        }
    }

    private Headers headers(AuditEventDto event) {
        RecordHeaders headers = new RecordHeaders();
        add(headers, EventTopics.Headers.EVENT_ID, event.eventId());
        add(headers, EventTopics.Headers.EVENT_TYPE, event.eventType());
        add(headers, EventTopics.Headers.SCHEMA_VERSION, String.valueOf(event.schemaVersion()));
        add(headers, EventTopics.Headers.SOURCE_SERVICE, event.sourceService());
        if (TraceContext.isValidTraceId(event.traceId())) {
            add(headers, EventTopics.Headers.TRACEPARENT, TraceContext.traceparent(event.traceId()));
        }
        return headers;
    }

    private static void add(RecordHeaders headers, String name, String value) {
        if (value != null) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
