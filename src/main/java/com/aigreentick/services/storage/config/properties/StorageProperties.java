package com.aigreentick.services.storage.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code storage.*}
 *
 * <p>{@code accessKey} and {@code secretKey} are deliberately ABSENT from this
 * schema so they cannot be set even intentionally. S3 access uses an instance role
 * (IRSA). The predecessor committed a live key pair in {@code application-storage.yml}.
 */
@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @NotBlank String activeProvider,
        @Valid @NotNull LocalProperties local,
        @Valid @NotNull S3Properties s3,
        @Valid PresignedUploadProperties presignedUpload,

        /**
         * Maximum files accepted by {@code POST /media/upload/batch}.
         *
         * <p>Bounds the work one request can queue: the batch is sequential, so
         * this multiplied by the per-file upload time is how long one servlet
         * thread can be held.
         *
         * <p>Note this is NOT the same protection as the multipart request cap.
         * See the comment on {@code spring.servlet.multipart.max-request-size}.
         */
        @DefaultValue("20") @Min(1) int maxFilesPerBatch) {

    /**
     * Direct client-to-storage upload.
     *
     * <p>OFF by default. This service targets small files — WhatsApp media around
     * 16 MB and smaller application assets — and at that size the proxied path is a
     * single round trip that costs the client nothing and the service very little.
     * The three-call protocol only earns its complexity when file size starts
     * dominating request duration (ADR-004, revised).
     *
     * <p>The machinery stays wired so raising a limit for one tenant is a YAML flip
     * rather than a re-architecture: enable this, set the threshold, done.
     */
    public record PresignedUploadProperties(
            boolean enabled,
            /** Files above this size use presigned URLs, when enabled. */
            long thresholdBytes) {
    }

    public record LocalProperties(
            @NotBlank String rootPath,
            @NotBlank String baseUrl) {
    }

    public record S3Properties(
            String bucket,
            String region,
            /** Set only for MinIO or another S3-compatible endpoint. */
            String endpoint,
            String cloudfrontDomain,
            String kmsKeyId,
            @DefaultValue("INTELLIGENT_TIERING") String storageClass,
            @DefaultValue("104857600") @Min(5_242_880L) long multipartThresholdBytes,
            @DefaultValue("16777216") @Min(5_242_880L) long partSizeBytes,
            @DefaultValue("15") @Min(1) int presignExpiryMinutes,
            @DefaultValue("true") boolean pathStyleAccess) {
    }

    /** True only when direct upload is both enabled and supported by the provider. */
    public boolean presignedUploadEnabled() {
        return presignedUpload != null && presignedUpload.enabled();
    }

    public long presignedThresholdBytes() {
        return presignedUpload == null ? Long.MAX_VALUE : presignedUpload.thresholdBytes();
    }

    public boolean isLocal() {
        return "local".equalsIgnoreCase(activeProvider);
    }

    public boolean isMinio() {
        return "minio".equalsIgnoreCase(activeProvider);
    }

    public boolean isS3Family() {
        return !isLocal();
    }
}
