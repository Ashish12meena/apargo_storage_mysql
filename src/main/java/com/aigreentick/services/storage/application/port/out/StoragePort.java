package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.domain.media.MediaType;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The storage backend, as the application needs it.
 *
 * <p>Implementations MUST NOT swallow failures. The predecessor fell back from a
 * failed presign to an unsigned URL for a PRIVATE object — a URL guaranteed to
 * return 403, surfacing to the user as a broken file rather than a clean error.
 */
public interface StoragePort {

    StoredObject put(InputStream content, PutRequest request);

    /** Metadata only. Used to verify that a direct upload actually landed. */
    Optional<StoredObject> head(StorageKey key);

    InputStream read(StorageKey key);

    /** Bounded read — content inspection never pulls a whole object. */
    byte[] readRange(StorageKey key, long offset, int length);

    /** Idempotent: deleting an absent key is a success, not an error. */
    boolean delete(StorageKey key);

    int deleteAll(List<StorageKey> keys);

    /** Time-limited, single-key, size-and-type-constrained upload grant. */
    PresignedUpload presignPut(PresignRequest request);

    PresignedUpload presignMultipart(PresignRequest request, int partCount);

    /** Completes a provider-side multipart upload. Returns the final object. */
    StoredObject completeMultipart(StorageKey key, String providerUploadId, List<PartRef> parts);

    void abortMultipart(StorageKey key, String providerUploadId);

    /** Time-limited read grant. Prefers a CDN URL when one is configured. */
    String presignGet(StorageKey key, Duration ttl);

    /** Paged listing under a prefix. Orphan detection only. */
    KeyPage listKeys(String prefix, String cursor, int limit);

    boolean isHealthy();

    ProviderType providerType();

    /** False for backends that cannot presign, e.g. the local filesystem. */
    boolean supportsPresignedUpload();

    // ── Contracts ───────────────────────────────────────────────────────────

    record PutRequest(StorageKey key, ByteSize size, String contentType,
                      MediaType mediaType, long orgId, long projectId) {
    }

    record PresignRequest(StorageKey key, ByteSize exactSize, String contentType, Duration ttl) {
    }

    record StoredObject(StorageKey key, ByteSize size, String contentType,
                        String etag, String checksumSha256, Instant lastModified) {
    }

    record PresignedUpload(String providerUploadId, List<String> urls, Instant expiresAt,
                           Map<String, String> requiredHeaders, long partSizeBytes) {
    }

    record PartRef(int partNumber, String etag) {
    }

    record KeyPage(List<StorageKey> keys, String nextCursor, boolean hasMore) {
    }

    enum ProviderType {
        LOCAL,
        S3,
        MINIO
    }
}
