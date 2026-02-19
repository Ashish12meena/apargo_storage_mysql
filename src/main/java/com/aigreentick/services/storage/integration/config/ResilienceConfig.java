package com.aigreentick.services.storage.integration.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.aigreentick.services.storage.integration.properties.ResilienceProperties;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Resilience4j configuration for media service client operations.
 * Provides retry, circuit breaker, and rate limiter policies.
 */
@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {
    private final ResilienceProperties properties;

    // ---------------- Retry Configuration ----------------

    @Bean
    public RetryConfig defaultRetryConfig() {
        
        return RetryConfig.custom()
                .maxAttempts(properties.getRetryMaxAttempts())
                .waitDuration(Duration.ofMillis(properties.getRetryWaitDurationMs()))
                .retryExceptions(IOException.class, WebClientRequestException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
    }

    @Bean
    public RetryRegistry retryRegistry(RetryConfig defaultRetryConfig) {
        return RetryRegistry.of(defaultRetryConfig);
    }

    @Bean
    public Retry defaultRetry(RetryRegistry retryRegistry) {
        return retryRegistry.retry("defaultRetry");
    }

    // ---------------- CircuitBreaker Configuration ----------------

     @Bean
    public CircuitBreakerConfig defaultCircuitBreakerConfig() {
        
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.getCircuitBreakerSlidingWindowSize())
                .failureRateThreshold(properties.getCircuitBreakerFailureRateThreshold())
                .slowCallRateThreshold(properties.getCircuitBreakerSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofSeconds(properties.getCircuitBreakerSlowCallDurationThresholdSec()))
                .waitDurationInOpenState(Duration.ofSeconds(properties.getCircuitBreakerWaitDurationInOpenStateSec()))
                .permittedNumberOfCallsInHalfOpenState(properties.getCircuitBreakerPermittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(properties.isCircuitBreakerAutomaticTransitionEnabled())
                .minimumNumberOfCalls(properties.getCircuitBreakerMinimumNumberOfCalls())
                .build();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerConfig defaultCircuitBreakerConfig) {
        return CircuitBreakerRegistry.of(defaultCircuitBreakerConfig);
    }

    // ---------------- RateLimiter Configuration ----------------

    @Bean
    public RateLimiterConfig defaultRateLimiterConfig() {
        
        return RateLimiterConfig.custom()
                .limitForPeriod(properties.getRateLimiterLimitForPeriod())
                .limitRefreshPeriod(Duration.ofSeconds(properties.getRateLimiterLimitRefreshPeriodSec()))
                .timeoutDuration(Duration.ofMillis(properties.getRateLimiterTimeoutDurationMs()))
                .build();
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(RateLimiterConfig defaultRateLimiterConfig) {
        return RateLimiterRegistry.of(defaultRateLimiterConfig);
    }
}