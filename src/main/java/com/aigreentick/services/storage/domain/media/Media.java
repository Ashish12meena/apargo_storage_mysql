package com.aigreentick.services.storage.domain.media;

import com.aigreentick.services.storage.domain.exception.IllegalMediaStateException;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.time.Instant;
import java.util.Objects;

/**
 * AGGREGATE ROOT — a stored file and its metadata.
 *
 * <p>State transitions are methods, not setters, so an illegal transition cannot
 * be expressed. The consistency boundary is one row: quota is a separate
 * aggregate updated in the same transaction, because merging them would serialise
 * every upload in a project behind one lock.
 */
public final class Media {

    private MediaId id;
    private final TenantRef tenant;
    private final StorageKey storageKey;
    private final String originalFilename;
    private ContentType contentType;
    private ByteSize size;
    private MediaType mediaType;
    private MediaStatus status;
    private ScanStatus scanStatus;
    private Checksum checksum;
    private String uploadSessionId;
    private final Long createdBy;
    private Long deletedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private Instant purgeAfter;
    private long version;

    private Media(MediaId id, TenantRef tenant, StorageKey storageKey, String originalFilename,
                  ContentType contentType, ByteSize size, MediaType mediaType, MediaStatus status,
                  ScanStatus scanStatus, Checksum checksum, String uploadSessionId, Long createdBy,
                  Long deletedBy, Instant createdAt, Instant updatedAt, Instant deletedAt,
                  Instant purgeAfter, long version) {
        this.id = id;
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        this.contentType = contentType;
        this.size = size;
        this.mediaType = mediaType;
        this.status = status;
        this.scanStatus = scanStatus;
        this.checksum = checksum;
        this.uploadSessionId = uploadSessionId;
        this.createdBy = createdBy;
        this.deletedBy = deletedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.purgeAfter = purgeAfter;
        this.version = version;
    }

    /**
     * The intent record, written BEFORE any bytes exist. This ordering is why an
     * orphaned object cannot occur: a crash leaves a sweepable row, never an
     * invisible object.
     */
    public static Media pending(TenantRef tenant, StorageKey key, String originalFilename,
                                ByteSize declaredSize, ContentType type, MediaType mediaType,
                                String uploadSessionId, Actor actor, Instant now) {
        return new Media(null, tenant, key, originalFilename, type, declaredSize, mediaType,
                MediaStatus.PENDING, ScanStatus.SKIPPED, null, uploadSessionId,
                actor == null ? null : actor.userIdAsLong(), null, now, now, null, null, 0L);
    }

    /** Rehydration from persistence. Bypasses transition rules by design. */
    public static Media rehydrate(MediaId id, TenantRef tenant, StorageKey storageKey, String originalFilename,
                                  ContentType contentType, ByteSize size, MediaType mediaType, MediaStatus status,
                                  ScanStatus scanStatus, Checksum checksum, String uploadSessionId, Long createdBy,
                                  Long deletedBy, Instant createdAt, Instant updatedAt, Instant deletedAt,
                                  Instant purgeAfter, long version) {
        return new Media(id, tenant, storageKey, originalFilename, contentType, size, mediaType, status,
                scanStatus, checksum, uploadSessionId, createdBy, deletedBy, createdAt, updatedAt,
                deletedAt, purgeAfter, version);
    }

    // ── Transitions ─────────────────────────────────────────────────────────

    public void confirm(ByteSize actualSize, Checksum actualChecksum, ContentType detected, Instant now) {
        requireTransition(MediaStatus.ACTIVE);
        this.size = Objects.requireNonNull(actualSize, "actualSize");
        this.checksum = actualChecksum;
        this.contentType = Objects.requireNonNull(detected, "detected");
        this.mediaType = MediaType.fromMimeType(detected.detected());
        this.status = MediaStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        requireTransition(MediaStatus.EXPIRED);
        this.status = MediaStatus.EXPIRED;
        this.updatedAt = now;
    }

    /**
     * @param purgeAfter when the object becomes removable. {@code deletedAt} records
     *                   WHEN the delete happened and never moves; this records WHEN
     *                   policy allows removal. A routine delete passes
     *                   {@code now + grace}; a compliance erasure passes {@code now}.
     */
    public void softDelete(Actor actor, Instant now, Instant purgeAfter) {
        requireTransition(MediaStatus.DELETED);
        this.status = MediaStatus.DELETED;
        this.deletedAt = now;
        this.purgeAfter = purgeAfter;
        this.deletedBy = actor == null ? null : actor.userIdAsLong();
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        requireTransition(MediaStatus.ACTIVE);
        this.status = MediaStatus.ACTIVE;
        this.deletedAt = null;
        this.purgeAfter = null;
        this.deletedBy = null;
        this.updatedAt = now;
    }

    public void markPurged(Instant now) {
        requireTransition(MediaStatus.PURGED);
        this.status = MediaStatus.PURGED;
        this.updatedAt = now;
    }

    public void quarantine(Instant now) {
        requireTransition(MediaStatus.QUARANTINED);
        this.status = MediaStatus.QUARANTINED;
        this.scanStatus = ScanStatus.INFECTED;
        this.updatedAt = now;
    }

    public void recordScanResult(ScanStatus result, Instant now) {
        this.scanStatus = Objects.requireNonNull(result, "result");
        this.updatedAt = now;
        if (result == ScanStatus.INFECTED && status == MediaStatus.ACTIVE) {
            quarantine(now);
        }
    }

    private void requireTransition(MediaStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalMediaStateException(
                    "illegal transition " + status + " -> " + target + " for media " + id);
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    /** True when the caller may be served these bytes. */
    public boolean isReadable() {
        return status == MediaStatus.ACTIVE;
    }

    /** Restorable while the purge window has not opened. */
    public boolean isRestorableAt(Instant now) {
        return status == MediaStatus.DELETED && purgeAfter != null && purgeAfter.isAfter(now);
    }

    /** True once policy allows the stored object to be removed. */
    public boolean isPurgeableAt(Instant now) {
        return status == MediaStatus.DELETED
                && (purgeAfter == null || !purgeAfter.isAfter(now));
    }

    /** Bytes to release on delete: always the size recorded at confirm time. */
    public ByteSize billableSize() {
        return size == null ? ByteSize.ZERO : size;
    }

    public void assignId(MediaId assigned) {
        if (this.id != null) {
            throw new IllegalStateException("media id already assigned");
        }
        this.id = assigned;
    }

    public MediaId id() { return id; }
    public TenantRef tenant() { return tenant; }
    public StorageKey storageKey() { return storageKey; }
    public String originalFilename() { return originalFilename; }
    public ContentType contentType() { return contentType; }
    public ByteSize size() { return size; }
    public MediaType mediaType() { return mediaType; }
    public MediaStatus status() { return status; }
    public ScanStatus scanStatus() { return scanStatus; }
    public Checksum checksum() { return checksum; }
    public String uploadSessionId() { return uploadSessionId; }
    public Long createdBy() { return createdBy; }
    public Long deletedBy() { return deletedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant deletedAt() { return deletedAt; }
    public Instant purgeAfter() { return purgeAfter; }
    public long version() { return version; }
}
