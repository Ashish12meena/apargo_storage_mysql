package com.aigreentick.services.storage.integration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "resilience")
@Data
public class ResilienceProperties {
    
    // Retry properties
    private int retryMaxAttempts = 3;
    private long retryWaitDurationMs = 1000;
    
    // Circuit Breaker properties
    private int circuitBreakerSlidingWindowSize = 20;
    private float circuitBreakerFailureRateThreshold = 50.0f;
    private float circuitBreakerSlowCallRateThreshold = 50.0f;
    private long circuitBreakerSlowCallDurationThresholdSec = 2;
    private long circuitBreakerWaitDurationInOpenStateSec = 10;
    private int circuitBreakerPermittedCallsInHalfOpenState = 5;
    private boolean circuitBreakerAutomaticTransitionEnabled = true;
    private int circuitBreakerMinimumNumberOfCalls = 10;
    
    // Rate Limiter properties
    private int rateLimiterLimitForPeriod = 10;
    private long rateLimiterLimitRefreshPeriodSec = 1;
    private long rateLimiterTimeoutDurationMs = 0;
    
    private volatile boolean enabled = true;
}
