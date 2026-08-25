package com.aigreentick.services.storage.infrastructure.storage.s3;

import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.domain.exception.StorageOperationException;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements {@link StoragePort} against S3 or MinIO. The production backend.
 *
 * <p>Fixes relative to the predecessor, each of which was a live defect:
 * <ul>
 *   <li>{@link S3Presigner} is a SINGLETON injected here, not constructed and torn
 *       down inside try-with-resources on every call.</li>
 *   <li>A failed presign THROWS. The predecessor fell back to an unsigned URL for a
 *       PRIVATE object — a URL guaranteed to 403, reaching the user as a broken
 *       file rather than a clean error.</li>
 *   <li>CloudFront selection is live, not commented out. Without it every read is
 *       billed at S3 egress and the CDN is bypassed entirely.</li>
 *   <li>Server-side encryption is asserted on the request rather than assumed from
 *       an unverified bucket default.</li>
 * </ul>
 */
@Slf4j
public class S3StorageAdapter implements StoragePort {

    private static final int MAX_DELETE_BATCH = 1000;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final ProviderType providerType;

    public S3StorageAdapter(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
        this.providerType = properties.isMinio() ? ProviderType.MINIO : ProviderType.S3;
    }

    private String bucket() {
        return properties.s3().bucket();
    }

    @Override
    public StoredObject put(InputStream content, PutRequest request) {
        try {
            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(request.key().value())
                    .contentType(request.contentType())
                    .contentLength(request.size().value())
                    .acl(ObjectCannedACL.PRIVATE)
                    .storageClass(StorageClass.fromValue(properties.s3().storageClass()))
                    .metadata(Map.of("org-id", String.valueOf(request.orgId()),
                            "project-id", String.valueOf(request.projectId()),
                            "media-type", request.mediaType().name()));

            applyEncryption(builder);

            var response = s3.putObject(builder.build(),
                    RequestBody.fromInputStream(content, request.size().value()));

            return new StoredObject(request.key(), request.size(), request.contentType(),
                    response.eTag(), response.checksumSHA256(), Instant.now());

        } catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException e) {
            throw new StorageOperationException(providerType.name(), "put",
                    "key=" + request.key().value(), e);
        }
    }

    /**
     * SSE is set explicitly. Relying on a bucket default means an unrelated bucket
     * policy change silently stops encrypting new objects.
     */
    private void applyEncryption(PutObjectRequest.Builder builder) {
        String kmsKeyId = properties.s3().kmsKeyId();
        if (kmsKeyId != null && !kmsKeyId.isBlank()) {
            builder.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(kmsKeyId);
        } else if (!properties.isMinio()) {
            builder.serverSideEncryption(ServerSideEncryption.AES256);
        }
    }

    @Override
    public Optional<StoredObject> head(StorageKey key) {
        try {
            HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket()).key(key.value()).build());
            return Optional.of(new StoredObject(key, ByteSize.of(response.contentLength()),
                    response.contentType(), response.eTag(), response.checksumSHA256(),
                    response.lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageOperationException(providerType.name(), "head", "key=" + key.value(), e);
        }
    }

    @Override
    public InputStream read(StorageKey key) {
        try {
            ResponseInputStream<?> stream = s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket()).key(key.value()).build());
            return stream;
        } catch (S3Exception e) {
            throw new StorageOperationException(providerType.name(), "get", "key=" + key.value(), e);
        }
    }

    /** Bounded read. Content inspection never pulls a whole object. */
    @Override
    public byte[] readRange(StorageKey key, long offset, int length) {
        try {
            String range = "bytes=" + offset + "-" + (offset + length - 1);
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket()).key(key.value()).range(range).build()).asByteArray();
        } catch (S3Exception e) {
            throw new StorageOperationException(providerType.name(), "getRange", "key=" + key.value(), e);
        }
    }

    /** Idempotent: S3 returns success for an absent key, which is what we want. */
    @Override
    public boolean delete(StorageKey key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key.value()).build());
            return true;
        } catch (S3Exception e) {
            throw new StorageOperationException(providerType.name(), "delete", "key=" + key.value(), e);
        }
    }

    /** One call per 1000 keys instead of 1000 calls. */
    @Override
    public int deleteAll(List<StorageKey> keys) {
        int deleted = 0;
        for (int i = 0; i < keys.size(); i += MAX_DELETE_BATCH) {
            List<StorageKey> chunk = keys.subList(i, Math.min(i + MAX_DELETE_BATCH, keys.size()));
            List<ObjectIdentifier> identifiers = chunk.stream()
                    .map(k -> ObjectIdentifier.builder().key(k.value()).build()).toList();
            try {
                var response = s3.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket())
                        .delete(Delete.builder().objects(identifiers).quiet(true).build())
                        .build());
                deleted += chunk.size() - response.errors().size();
                response.errors().forEach(err ->
                        log.warn("batch delete failed for one key: {}", err.code()));
            } catch (S3Exception e) {
                throw new StorageOperationException(providerType.name(), "deleteBatch",
                        "chunk of " + chunk.size(), e);
            }
        }
        return deleted;
    }

    // ── Presigning ──────────────────────────────────────────────────────────

    /**
     * The grant is tightly scoped: fixed server-generated key, exact content
     * length, fixed content type, short expiry. A client cannot redirect it to
     * another key or under-declare its size.
     */
    @Override
    public PresignedUpload presignPut(PresignRequest request) {
        try {
            PutObjectRequest.Builder objectRequest = PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(request.key().value())
                    .contentType(request.contentType())
                    .contentLength(request.exactSize().value())
                    .acl(ObjectCannedACL.PRIVATE);
            applyEncryption(objectRequest);

            var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(request.ttl())
                    .putObjectRequest(objectRequest.build())
                    .build());

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", request.contentType());
            headers.put("Content-Length", String.valueOf(request.exactSize().value()));
            presigned.signedHeaders().forEach((name, values) -> {
                if (!values.isEmpty() && !"host".equalsIgnoreCase(name)) {
                    headers.put(name, values.get(0));
                }
            });

            return new PresignedUpload(null, List.of(presigned.url().toString()),
                    presigned.expiration(), headers, request.exactSize().value());

        } catch (RuntimeException e) {
            // No fallback to an unsigned URL: for a PRIVATE object that is a
            // guaranteed 403 that surfaces to the user as a broken file.
            throw new StorageOperationException(providerType.name(), "presignPut",
                    "key=" + request.key().value(), e);
        }
    }

    @Override
    public PresignedUpload presignMultipart(PresignRequest request, int partCount) {
        try {
            CreateMultipartUploadRequest.Builder create = CreateMultipartUploadRequest.builder()
                    .bucket(bucket())
                    .key(request.key().value())
                    .contentType(request.contentType())
                    .acl(ObjectCannedACL.PRIVATE);
            String kmsKeyId = properties.s3().kmsKeyId();
            if (kmsKeyId != null && !kmsKeyId.isBlank()) {
                create.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(kmsKeyId);
            }

            String uploadId = s3.createMultipartUpload(create.build()).uploadId();

            List<String> urls = new ArrayList<>(partCount);
            for (int part = 1; part <= partCount; part++) {
                UploadPartRequest partRequest = UploadPartRequest.builder()
                        .bucket(bucket()).key(request.key().value())
                        .uploadId(uploadId).partNumber(part).build();
                urls.add(presigner.presignUploadPart(UploadPartPresignRequest.builder()
                        .signatureDuration(request.ttl())
                        .uploadPartRequest(partRequest)
                        .build()).url().toString());
            }
            return new PresignedUpload(uploadId, urls, Instant.now().plus(request.ttl()),
                    Map.of("Content-Type", request.contentType()), properties.s3().partSizeBytes());

        } catch (RuntimeException e) {
            throw new StorageOperationException(providerType.name(), "presignMultipart",
                    "key=" + request.key().value(), e);
        }
    }

    @Override
    public StoredObject completeMultipart(StorageKey key, String providerUploadId, List<PartRef> parts) {
        try {
            List<CompletedPart> completed = parts.stream()
                    .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build())
                    .toList();
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket()).key(key.value()).uploadId(providerUploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                    .build());
            return head(key).orElseThrow(() -> new StorageOperationException(providerType.name(),
                    "completeMultipart", "object missing after completion", null));
        } catch (S3Exception e) {
            throw new StorageOperationException(providerType.name(), "completeMultipart",
                    "key=" + key.value(), e);
        }
    }

    @Override
    public void abortMultipart(StorageKey key, String providerUploadId) {
        if (providerUploadId == null) {
            return;
        }
        try {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket()).key(key.value()).uploadId(providerUploadId).build());
        } catch (S3Exception e) {
            // Incomplete parts bill silently, so this is also covered by an S3
            // lifecycle rule (AbortIncompleteMultipartUpload) as a backstop.
            log.warn("failed to abort multipart upload for key: {}", e.awsErrorDetails().errorCode());
        }
    }

    /**
     * Prefers CloudFront when configured. The predecessor had this commented out,
     * so every read was billed at S3 egress with the CDN bypassed.
     */
    @Override
    public String presignGet(StorageKey key, Duration ttl) {
        String cdn = properties.s3().cloudfrontDomain();
        if (cdn != null && !cdn.isBlank()) {
            // Objects are private; the distribution is expected to use an Origin
            // Access Identity, so the CDN URL needs no query signature here.
            return "https://" + cdn.replaceAll("/+$", "") + "/" + key.value();
        }
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket()).key(key.value()).build())
                    .build()).url().toString();
        } catch (RuntimeException e) {
            throw new StorageOperationException(providerType.name(), "presignGet",
                    "key=" + key.value(), e);
        }
    }

    @Override
    public KeyPage listKeys(String prefix, String cursor, int limit) {
        try {
            ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                    .bucket(bucket()).prefix(prefix).maxKeys(limit);
            if (cursor != null && !cursor.isBlank()) {
                request.continuationToken(cursor);
            }
            ListObjectsV2Response response = s3.listObjectsV2(request.build());
            List<StorageKey> keys = response.contents().stream()
                    .map(o -> new StorageKey(o.key())).toList();
            return new KeyPage(keys, response.nextContinuationToken(),
                    Boolean.TRUE.equals(response.isTruncated()));
        } catch (S3Exception e) {
            throw new StorageOperationException(providerType.name(), "list", "prefix=" + prefix, e);
        }
    }

    /** Actual reachability, not a default UP. Cached by the health indicator. */
    @Override
    public boolean isHealthy() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket()).build());
            return true;
        } catch (RuntimeException e) {
            log.warn("storage health check failed: {}", e.toString());
            return false;
        }
    }

    @Override
    public ProviderType providerType() {
        return providerType;
    }

    @Override
    public boolean supportsPresignedUpload() {
        return true;
    }
}
