package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.time.Instant;
import java.util.Optional;

/**
 * Retry safety for mutating endpoints.
 *
 * <p>{@code template-service} retries. In the predecessor a retry after a timeout
 * created a second file, a second row, and a second quota charge — and with no
 * delete endpoint, that quota could never be reclaimed.
 *
 * <p>Records are TENANT-SCOPED so keys cannot collide or be probed across orgs.
 * The request hash is stored so that reusing a key for a DIFFERENT request is
 * rejected rather than silently returning the wrong cached response.
 */
public interface IdempotencyPort {

    /**
     * @return empty when this is the first attempt (a reservation is recorded);
     *         present when a prior attempt exists and should be replayed
     */
    Optional<StoredResponse> beginOrReplay(TenantRef tenant, String key, String requestHash);

    void complete(TenantRef tenant, String key, int httpStatus, String responseJson);

    void fail(TenantRef tenant, String key, String reason);

    int deleteExpiredBefore(Instant cutoff);

    record StoredResponse(Status status, int httpStatus, String responseJson) {

        public enum Status {
            IN_PROGRESS,
            COMPLETED,
            FAILED
        }
    }
}
