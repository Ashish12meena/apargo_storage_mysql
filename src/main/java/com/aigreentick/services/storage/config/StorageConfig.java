package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.infrastructure.storage.local.LocalFileSystemStorageAdapter;
import com.aigreentick.services.storage.infrastructure.storage.s3.S3StorageAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;

/**
 * Selects exactly one {@link StoragePort}.
 *
 * <p>Replaces the predecessor's pair of {@code @ConditionalOnProperty} beans,
 * under which enabling both or neither was a silent misconfiguration — the second
 * case failing at the first upload with a missing-bean error rather than at boot.
 */
@Configuration
@Slf4j
public class StorageConfig {

    /**
     * SINGLETON. The predecessor constructed and closed a presigner inside
     * try-with-resources on every call, paying credential resolution and region
     * setup on the hottest small operation.
     */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.s3().region()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (hasEndpoint(properties)) {
            builder.endpointOverride(URI.create(properties.s3().endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties properties) {
        S3ClientBuilderHolder holder = new S3ClientBuilderHolder(properties);
        return holder.build();
    }

    private boolean hasEndpoint(StorageProperties properties) {
        return properties.s3().endpoint() != null && !properties.s3().endpoint().isBlank();
    }

    /** Keeps the SDK builder chain out of the bean method for readability. */
    private static final class S3ClientBuilderHolder {

        private final StorageProperties properties;

        private S3ClientBuilderHolder(StorageProperties properties) {
            this.properties = properties;
        }

        private S3Client build() {
            var builder = S3Client.builder()
                    .region(Region.of(properties.s3().region()))
                    // No static keys: IRSA / instance role only. The access-key
                    // properties are absent from the schema entirely.
                    .credentialsProvider(DefaultCredentialsProvider.create());

            if (properties.s3().endpoint() != null && !properties.s3().endpoint().isBlank()) {
                builder.endpointOverride(URI.create(properties.s3().endpoint()))
                        // MinIO needs path-style addressing; real S3 does not.
                        .serviceConfiguration(S3Configuration.builder()
                                .pathStyleAccessEnabled(properties.s3().pathStyleAccess())
                                .build());
            }
            return builder.build();
        }
    }

    @Bean
    public StoragePort storagePort(StorageProperties properties, S3Client s3Client,
                                   S3Presigner presigner) {
        if (properties.isLocal()) {
            log.warn("LOCAL filesystem storage selected. Development use only "
                    + "— production startup is blocked by StartupAssertions.");
            return new LocalFileSystemStorageAdapter(properties);
        }
        log.info("S3-family storage selected: provider={} bucket={} cdn={}",
                properties.activeProvider(), properties.s3().bucket(),
                properties.s3().cloudfrontDomain() == null || properties.s3().cloudfrontDomain().isBlank()
                        ? "NONE (reads billed at S3 egress)" : "configured");
        return new S3StorageAdapter(s3Client, presigner, properties);
    }
}
