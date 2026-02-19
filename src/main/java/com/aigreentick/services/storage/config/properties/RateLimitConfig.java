package com.aigreentick.services.storage.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting configuration using Bucket4j.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitConfig {
     private boolean enabled = true;
    private long defaultCapacity = 100;
    private long defaultRefillTokens = 100;
    private String defaultRefillDuration = "1m";
    private Map<String, EndpointRateLimit> endpoints = new ConcurrentHashMap<>();

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Get or create a rate limit bucket for a given key.
     */
    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> createNewBucket(defaultCapacity, defaultRefillTokens, defaultRefillDuration));
    }

    /**
     * Get or create a bucket with custom configuration.
     */
    public Bucket resolveBucket(String key, String endpointName) {
        EndpointRateLimit endpointConfig = endpoints.get(endpointName);
        if (endpointConfig != null) {
            return buckets.computeIfAbsent(key, k -> 
                createNewBucket(
                    endpointConfig.getCapacity(),
                    endpointConfig.getRefillTokens(),
                    endpointConfig.getRefillDuration()
                )
            );
        }
        return resolveBucket(key);
    }

    private Bucket createNewBucket(long capacity, long tokens, String duration) {
        Duration refillDuration = parseDuration(duration);
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(tokens, refillDuration));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private Duration parseDuration(String duration) {
        if (duration.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(duration.substring(0, duration.length() - 1)));
        } else if (duration.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(duration.substring(0, duration.length() - 1)));
        } else if (duration.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(duration.substring(0, duration.length() - 1)));
        }
        return Duration.ofMinutes(1);
    }

    @Data
    public static class EndpointRateLimit {
        private long capacity;
        private long refillTokens;
        private String refillDuration;
    }
}
