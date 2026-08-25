package com.aigreentick.services.storage.infrastructure.observability;

import com.aigreentick.services.storage.application.port.out.StoragePort;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Actual backend reachability. Actuator's default {@code UP} says nothing about
 * whether this pod can write to S3 or whether the local disk is full — the two
 * failure modes this service is most exposed to.
 *
 * <p>Cached for 30 seconds so a health-check storm cannot itself become load on
 * the storage backend.
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final StoragePort storage;
    private volatile Instant lastCheck = Instant.EPOCH;
    private volatile boolean lastResult;

    public StorageHealthIndicator(StoragePort storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        boolean healthy = cachedCheck();
        Health.Builder builder = healthy ? Health.up() : Health.down();
        return builder.withDetail("provider", storage.providerType().name())
                .withDetail("presignedUploads", storage.supportsPresignedUpload())
                .build();
    }

    private boolean cachedCheck() {
        Instant now = Instant.now();
        if (Duration.between(lastCheck, now).compareTo(CACHE_TTL) < 0) {
            return lastResult;
        }
        synchronized (this) {
            lastResult = storage.isHealthy();
            lastCheck = now;
            return lastResult;
        }
    }
}
