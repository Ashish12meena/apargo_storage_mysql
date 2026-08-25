package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * {@code rate-limit.*}
 *
 * @param failOpen defaults TRUE: refusing all traffic because the rate limiter is
 *                 down converts a degradation into an outage (ADR-007).
 */
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean failOpen,
        Map<String, Rule> rules,
        Map<String, Long> bandwidthBytesPerMinute) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
        bandwidthBytesPerMinute = bandwidthBytesPerMinute == null
                ? Map.of() : Map.copyOf(bandwidthBytesPerMinute);
    }

    public record Rule(long capacity, long refillTokens, Duration refillPeriod) {
    }

    public Rule rule(String name, Rule fallback) {
        return rules.getOrDefault(name, fallback);
    }
}
