package com.aigreentick.services.storage.service.impl.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.aigreentick.services.storage.config.properties.provider.S3StorageProperties;
import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.StorageProviderType;
import com.aigreentick.services.storage.exception.StorageException;
import com.aigreentick.services.storage.service.StorageProvider;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage.providers.s3", name = "enabled", havingValue = "true")
public class S3StorageAdapter implements StorageProvider {

    private final S3Client s3Client;
    private final S3TransferManager transferManager;
    private final S3StorageProperties properties;

    @Override
    public StorageResult save(InputStream inputStream, StorageMetadata metadata) throws StorageException {
        try {
            String storageKey = metadata.generateStorageKey();

            // Determine upload strategy based on file size
            if (metadata.getFileSize() < properties.getMultipartThresholdBytes()) {
                uploadSmallFile(inputStream, storageKey, metadata);
            } else {
                uploadLargeFile(inputStream, storageKey, metadata);
            }

            log.info("File uploaded to S3: bucket={}, key={}", properties.getBucket(), storageKey);

            return StorageResult.builder()
                    .storageKey(storageKey)
                    .publicUrl(generatePublicUrl(storageKey))
                    .provider(StorageProviderType.S3)
                    .bucket(properties.getBucket())
                    .region(properties.getRegion())
                    .fileSize(metadata.getFileSize())
                    .contentType(metadata.getContentType())
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            throw new StorageException("Failed to upload file to S3", e, StorageProviderType.S3);
        }
    }

    private void uploadSmallFile(InputStream inputStream, String storageKey, StorageMetadata metadata) {
        Map<String, String> metadataMap = buildMetadata(metadata);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .contentType(metadata.getContentType())
                .contentLength(metadata.getFileSize())
                .storageClass(properties.getStorageClass())
                .acl(ObjectCannedACL.PRIVATE)
                .metadata(metadataMap)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, metadata.getFileSize()));
    }

    private void uploadLargeFile(InputStream inputStream, String storageKey, StorageMetadata metadata) {
        Map<String, String> metadataMap = buildMetadata(metadata);

        UploadRequest uploadRequest = UploadRequest.builder()
                .putObjectRequest(req -> req
                        .bucket(properties.getBucket())
                        .key(storageKey)
                        .contentType(metadata.getContentType())
                        .acl(ObjectCannedACL.PRIVATE)
                        .storageClass(properties.getStorageClass())
                        .metadata(metadataMap))
                .requestBody(AsyncRequestBody.fromInputStream(inputStream, metadata.getFileSize(), null))
                .build();

        Upload upload = transferManager.upload(uploadRequest);
        @SuppressWarnings("unused")
        CompletedUpload completed = upload.completionFuture().join();

        log.info("Multipart upload completed for key: {}", storageKey);
    }

    @Override
    public InputStream retrieve(String storageKey) throws StorageException {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build();

            return s3Client.getObject(getRequest);

        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: {}", storageKey);
            throw new StorageException("File not found in S3: " + storageKey, e, StorageProviderType.S3, storageKey);
        } catch (Exception e) {
            log.error("Failed to retrieve file from S3: {}", storageKey, e);
            throw new StorageException("Failed to retrieve file from S3", e, StorageProviderType.S3, storageKey);
        }
    }

    @Override
    public boolean delete(String storageKey) throws StorageException {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("File deleted from S3: {}", storageKey);
            return true;

        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", storageKey, e);
            throw new StorageException("Failed to delete file from S3", e, StorageProviderType.S3, storageKey);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if file exists in S3: {}", storageKey, e);
            return false;
        }
    }

    @Override
    public String getPublicUrl(String storageKey, Duration expiry) {
        if (expiry != null) {
            return generatePresignedUrl(storageKey, expiry);
        }
        return generatePublicUrl(storageKey);
    }

    @Override
    public StorageProviderType getProviderType() {
        return StorageProviderType.S3;
    }

    private String generatePublicUrl(String storageKey) {
        // Use CloudFront if configured
        // if (properties.getCloudfrontDomain() != null &&
        // !properties.getCloudfrontDomain().isEmpty()) {
        // return properties.getCloudfrontDomain() + "/" + storageKey;
        // }

        // Otherwise use S3 URL
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                properties.getBucket(),
                properties.getRegion(),
                storageKey);
    }

    private String generatePresignedUrl(String storageKey, Duration expiry) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(properties.getRegion()))
                .build()) {

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(getRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", storageKey, e);
            return generatePublicUrl(storageKey);
        }
    }

    private Map<String, String> buildMetadata(StorageMetadata metadata) {
        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put("original-filename", metadata.getOriginalFilename());
        metadataMap.put("project-id", String.valueOf(metadata.getProjectId()));
        metadataMap.put("org-id", String.valueOf(metadata.getOrganisationId()));
        metadataMap.put("media-type", metadata.getMediaType().name());
        return metadataMap;
    }
}