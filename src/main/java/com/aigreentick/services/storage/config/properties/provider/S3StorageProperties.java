package com.aigreentick.services.storage.config.properties.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage.providers.s3")
public class S3StorageProperties {
    private boolean enabled = false;
    private String bucket;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private boolean useIamRole = true;
    private String endpoint; // For S3-compatible services like MinIO
    private String cloudfrontDomain;
    private String storageClass = "INTELLIGENT_TIERING";
    private long multipartThresholdBytes = 104857600L; // 100MB
    private int presignedUrlExpiryMinutes = 15;
}