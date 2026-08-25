package com.aigreentick.services.storage.domain.upload;

import com.aigreentick.services.storage.domain.exception.IllegalMediaStateException;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.time.Instant;
import java.util.Objects;

/**
 * AGGREGATE ROOT — a quota reservation with a TTL, bound to one prospective object.
 *
 * <p>Makes {@code reserve → write bytes → record metadata} safe at every
 * interruption point. Without it, a crash between steps two and three leaves a
 * stored object with no row: invisible to reconciliation, unreclaimable, billed
 * indefinitely.
 */
public final class UploadSession {

    private final UploadSessionId id;
    private final TenantRef tenant;
    private final StorageKey storageKey;
    private final UploadMode mode;
    private final ByteSize declaredSize;
    private UploadSessionStatus status;
    private MediaId mediaId;
    private final String idempotencyKey;
    private String providerUploadId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant completedAt;

    private UploadSession(UploadSessionId id, TenantRef tenant, StorageKey storageKey, UploadMode mode,
                          ByteSize declaredSize, UploadSessionStatus status, MediaId mediaId,
                          String idempotencyKey, String providerUploadId, Instant createdAt,
                          Instant expiresAt, Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.declaredSize = Objects.requireNonNull(declaredSize, "declaredSize");
        this.status = Objects.requireNonNull(status, "status");
        this.mediaId = mediaId;
        this.idempotencyKey = idempotencyKey;
        this.providerUploadId = providerUploadId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
    }

    public static UploadSession open(TenantRef tenant, StorageKey key, UploadMode mode,
                                     ByteSize declaredSize, Instant now, Instant expiresAt,
                                     String idempotencyKey) {
        return new UploadSession(UploadSessionId.generate(), tenant, key, mode, declaredSize,
                UploadSessionStatus.RESERVED, null, idempotencyKey, null, now, expiresAt, null);
    }

    public static UploadSession rehydrate(UploadSessionId id, TenantRef tenant, StorageKey key, UploadMode mode,
                                          ByteSize declaredSize, UploadSessionStatus status, MediaId mediaId,
                                          String idempotencyKey, String providerUploadId, Instant createdAt,
                                          Instant expiresAt, Instant completedAt) {
        return new UploadSession(id, tenant, key, mode, declaredSize, status, mediaId, idempotencyKey,
                providerUploadId, createdAt, expiresAt, completedAt);
    }

    public void commit(Instant now) {
        requireReserved("commit");
        this.status = UploadSessionStatus.COMMITTED;
        this.completedAt = now;
    }

    public void abort(Instant now) {
        requireReserved("abort");
        this.status = UploadSessionStatus.ABORTED;
        this.completedAt = now;
    }

    public void expire(Instant now) {
        requireReserved("expire");
        this.status = UploadSessionStatus.EXPIRED;
        this.completedAt = now;
    }

    private void requireReserved(String operation) {
        if (status != UploadSessionStatus.RESERVED) {
            throw new IllegalMediaStateException(
                    "cannot " + operation + " session " + id.value() + " in status " + status);
        }
    }

    public void attachMedia(MediaId assigned) {
        this.mediaId = assigned;
    }

    public void attachProviderUploadId(String providerId) {
        this.providerUploadId = providerId;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** The sweeper's predicate: still holding quota, past its TTL. */
    public boolean isReclaimable(Instant now) {
        return status.holdsQuota() && isExpiredAt(now);
    }

    public UploadSessionId id() { return id; }
    public TenantRef tenant() { return tenant; }
    public StorageKey storageKey() { return storageKey; }
    public UploadMode mode() { return mode; }
    public ByteSize declaredSize() { return declaredSize; }
    public UploadSessionStatus status() { return status; }
    public MediaId mediaId() { return mediaId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String providerUploadId() { return providerUploadId; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant completedAt() { return completedAt; }
}
