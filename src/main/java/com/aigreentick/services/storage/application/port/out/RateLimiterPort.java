package com.aigreentick.services.storage.application.port.out;

import java.time.Duration;

/**
 * Distributed rate limiting.
 *
 * <p>The predecessor kept buckets in a JVM-local map: with N replicas the effective
 * limit is N times the configured value, and whether a caller is throttled depends
 * on which pod the load balancer picked.
 *
 * <p>FAILS OPEN when the backing store is unreachable (ADR-007). Rejecting all
 * traffic because the rate limiter is down converts a degradation into an outage.
 */
public interface RateLimiterPort {

    Decision tryConsume(String key, long tokens, Limit limit);

    record Limit(long capacity, long refillTokens, Duration refillPeriod) {
    }

    record Decision(boolean allowed, long limit, long remaining, Duration retryAfter, boolean degraded) {

        public static Decision allowedDegraded(long limit) {
            return new Decision(true, limit, limit, Duration.ZERO, true);
        }
    }
}
