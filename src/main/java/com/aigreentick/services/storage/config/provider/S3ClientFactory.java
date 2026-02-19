package com.aigreentick.services.storage.config.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aigreentick.services.storage.config.properties.provider.S3StorageProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage.providers.s3", name = "enabled", havingValue = "true")
public class S3ClientFactory {
    
    private final S3StorageProperties properties;
    
    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3 Client for bucket: {} in region: {}", 
                 properties.getBucket(), properties.getRegion());
        
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(getCredentialsProvider());
        
        // Custom endpoint for S3-compatible services (MinIO, LocalStack)
        if (properties.getEndpoint() != null && !properties.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            log.info("Using custom S3 endpoint: {}", properties.getEndpoint());
        }
        
        return builder.build();
    }
    
    @Bean
    public S3AsyncClient s3AsyncClient() {
        log.info("Initializing S3 Async Client for bucket: {} in region: {}", 
                 properties.getBucket(), properties.getRegion());
        
        S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(getCredentialsProvider());
        
        // Custom endpoint for S3-compatible services (MinIO, LocalStack)
        if (properties.getEndpoint() != null && !properties.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            log.info("Using custom S3 async endpoint: {}", properties.getEndpoint());
        }
        
        return builder.build();
    }
    
    @Bean
    public S3TransferManager s3TransferManager(S3AsyncClient s3AsyncClient) {
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }
    
    private AwsCredentialsProvider getCredentialsProvider() {
        if (properties.isUseIamRole()) {
            log.info("Using IAM role for AWS authentication");
            return DefaultCredentialsProvider.create();
        } else {
            log.info("Using access key for AWS authentication");
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    properties.getAccessKey(),
                    properties.getSecretKey()
            );
            return StaticCredentialsProvider.create(credentials);
        }
    }
}