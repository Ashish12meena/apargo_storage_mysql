package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.out.IdempotencyPort;
import com.aigreentick.services.storage.common.util.Sha256;
import com.aigreentick.services.storage.domain.exception.RequestInProgressException;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a mutating operation in an idempotency record.
 *
 * <p>{@code template-service} retries. In the predecessor a retry after a timeout
 * created a second file, a second row, and a second quota charge — and with no
 * delete endpoint, that quota could never be reclaimed.
 *
 * <p>The stored response is the media ID, not a serialised body. Replay re-reads
 * the row, so a cached response can never drift from the record it describes, and
 * no JSON mapper is needed in the application layer.
 */
@Component
@Slf4j
public class IdempotencyGuard {

    private final IdempotencyPort idempotency;

    public IdempotencyGuard(IdempotencyPort idempotency) {
        this.idempotency = idempotency;
    }

    /**
     * @param key    client-supplied {@code Idempotency-Key}; when null the
     *               operation runs unguarded
     * @param replay resolves a previously-stored media id back to a result
     * @param action the operation itself, returning the media id and the result
     */
    public <T> T execute(TenantRef tenant, String key, String requestHash,
                         Function<String, Optional<T>> replay,
                         Supplier<Recorded<T>> action) {

        if (key == null || key.isBlank()) {
            return action.get().result();
        }

        // Throws IdempotencyConflictException (422) when the same key arrives with
        // a different payload — silently replaying the wrong response would be worse.
        Optional<IdempotencyPort.StoredResponse> existing =
                idempotency.beginOrReplay(tenant, key, requestHash);

        if (existing.isPresent()) {
            IdempotencyPort.StoredResponse stored = existing.get();
            if (stored.status() == IdempotencyPort.StoredResponse.Status.IN_PROGRESS) {
                throw new RequestInProgressException("idempotency key " + key + " is in flight");
            }
            Optional<T> replayed = replay.apply(stored.responseJson());
            if (replayed.isPresent()) {
                log.info("replayed idempotent request for key {}", key);
                return replayed.get();
            }
            // The record survived but its media row did not — treat as a fresh
            // attempt rather than failing a legitimate retry.
            log.warn("idempotency record for key {} has no resolvable result; re-running", key);
        }

        try {
            Recorded<T> recorded = action.get();
            idempotency.complete(tenant, key, 201, recorded.mediaId());
            return recorded.result();
        } catch (RuntimeException e) {
            // Deleted rather than marked FAILED: a failed attempt must not block a
            // legitimate retry with the same key.
            idempotency.fail(tenant, key, e.toString());
            throw e;
        }
    }

    /** Canonical request fingerprint. Any material change produces a new hash. */
    public String hashUpload(TenantRef tenant, String filename, String contentType, ByteSize size) {
        return Sha256.ofUtf8(tenant.orgId() + "|" + tenant.projectId() + "|"
                + filename + "|" + contentType + "|" + size.value());
    }

    /** The outcome of a guarded action: what to store, and what to return. */
    public record Recorded<T>(String mediaId, T result) {
    }
}
