package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.IdempotencyPort;
import com.aigreentick.services.storage.domain.exception.IdempotencyConflictException;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.aigreentick.services.storage.infrastructure.persistence.entity.IdempotencyRecordId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Implements {@link IdempotencyPort}.
 *
 * <p>Concurrent duplicates are detected by the UNIQUE-key violation on insert, not
 * by a read-then-write, which would have its own race. {@code saveAndFlush} is
 * required: without the flush the constraint violation would not surface until the
 * transaction commits, long after the decision has been made.
 *
 * <p>{@code REQUIRES_NEW}: the reservation must survive the caller's rollback,
 * otherwise a failed request would erase its own record and a retry would look like
 * a first attempt.
 */
@Repository
public class IdempotencyAdapter implements IdempotencyPort {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyJpaRepository jpa;

    public IdempotencyAdapter(IdempotencyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StoredResponse> beginOrReplay(TenantRef tenant, String key, String requestHash) {
        Instant now = Instant.now();
        try {
            jpa.saveAndFlush(new IdempotencyRecordEntity(tenant.orgId(), tenant.projectId(), key,
                    requestHash, StoredResponse.Status.IN_PROGRESS.name(), now, now.plus(RETENTION)));
            return Optional.empty();

        } catch (DataIntegrityViolationException e) {
            return Optional.of(existing(tenant, key, requestHash));
        }
    }

    private StoredResponse existing(TenantRef tenant, String key, String requestHash) {
        IdempotencyRecordEntity row = jpa
                .findById(new IdempotencyRecordId(tenant.orgId(), tenant.projectId(), key))
                .orElseThrow(() -> new IllegalStateException("duplicate key without a row"));

        if (!row.getRequestHash().equals(requestHash)) {
            // Same key, different payload. Returning the cached response would be
            // worse than an error, so this surfaces as 422.
            throw new IdempotencyConflictException("key reused with a different payload");
        }
        return new StoredResponse(
                StoredResponse.Status.valueOf(row.getStatus()),
                row.getResponseStatus() == null ? 0 : row.getResponseStatus(),
                row.getResponseBody());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(TenantRef tenant, String key, int httpStatus, String responseJson) {
        jpa.findById(new IdempotencyRecordId(tenant.orgId(), tenant.projectId(), key))
                .ifPresent(row -> {
                    row.setStatus(StoredResponse.Status.COMPLETED.name());
                    row.setResponseStatus(httpStatus);
                    row.setResponseBody(responseJson);
                    jpa.save(row);
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(TenantRef tenant, String key, String reason) {
        // Deleted rather than marked FAILED: a failed attempt must not block a
        // legitimate retry with the same key.
        IdempotencyRecordId id = new IdempotencyRecordId(tenant.orgId(), tenant.projectId(), key);
        jpa.findById(id)
                .filter(row -> StoredResponse.Status.IN_PROGRESS.name().equals(row.getStatus()))
                .ifPresent(jpa::delete);
    }

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant cutoff) {
        return jpa.deleteExpired(cutoff);
    }
}
