package com.aigreentick.services.storage.infrastructure.outbox;

import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.EventPublisherPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.service.TenantTeardownService;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes a tenant teardown in bounded batches.
 *
 * <p>Each pass marks up to {@code quota.teardown-batch-size} rows, then RE-APPENDS the request
 * event if any remain. That continuation pattern reuses the outbox's retry,
 * backoff, and dead-letter machinery instead of inventing a second job system, and
 * it means a crash mid-teardown simply resumes from wherever it stopped — the
 * predicate is "rows still live", not a stored offset.
 */
@Component
@Slf4j
public class TenantTeardownHandler implements EventPublisherPort {

    /** Guards against a bug turning into an unbounded self-requeue loop. */
    private static final int MAX_BATCHES = 100_000;

    private final TenantTeardownService teardownService;
    private final MediaRepositoryPort mediaRepository;
    private final OutboxPort outbox;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    public TenantTeardownHandler(TenantTeardownService teardownService,
                                 MediaRepositoryPort mediaRepository, OutboxPort outbox,
                                 ClockPort clock, ObjectMapper objectMapper, MeterRegistry meters) {
        this.teardownService = teardownService;
        this.mediaRepository = mediaRepository;
        this.outbox = outbox;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.meters = meters;
    }

    @Override
    public String eventType() {
        return "tenant.teardown.requested";
    }

    @Override
    public void handle(OutboxPort.OutboxRecord record) {
        JsonNode p = parse(record.payloadJson());
        long orgId = p.path("orgId").asLong();
        Long projectId = p.hasNonNull("projectId") ? p.path("projectId").asLong() : null;
        boolean permanent = p.path("permanent").asBoolean(false);
        String requestedBy = p.path("requestedBy").asText(null);
        int batchesDone = p.path("batchesDone").asInt(0);
        long filesRemovedSoFar = p.path("filesRemoved").asLong(0);
        String handle = record.aggregateId();

        if (batchesDone >= MAX_BATCHES) {
            // Fail loudly rather than requeue forever. Reaching this means rows are
            // not transitioning, which is a bug, not a big tenant.
            throw new IllegalStateException("teardown " + handle + " exceeded " + MAX_BATCHES
                    + " batches; aborting to avoid an unbounded loop");
        }

        int affected = teardownService.teardownBatch(orgId, projectId, permanent, requestedBy);
        meters.counter("storage.teardown.files_marked").increment(affected);

        long totalRemoved = filesRemovedSoFar + affected;
        long remaining = mediaRepository.countLiveForMaintenance(orgId, projectId);

        if (remaining > 0) {
            log.info("teardown {} batch {} marked {} file(s), {} total, {} remaining",
                    handle, batchesDone + 1, affected, totalRemoved, remaining);
            outbox.append(new DomainEvent.TenantTeardownRequested(handle, orgId, projectId,
                    permanent, requestedBy, batchesDone + 1, totalRemoved, clock.now()));
            return;
        }

        // No live rows left: settle quota and close out. The purge scan removes the
        // stored objects on its own schedule.
        teardownService.settleQuota(orgId, projectId);
        outbox.append(new DomainEvent.TenantTeardownCompleted(handle, orgId, projectId,
                totalRemoved, clock.now()));

        log.warn("TENANT TEARDOWN COMPLETE: handle={} org={} project={} removed={} file(s) "
                        + "in {} batch(es)",
                handle, orgId, projectId == null ? "ALL" : projectId, totalRemoved, batchesDone + 1);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("unreadable teardown payload", e);
        }
    }
}
