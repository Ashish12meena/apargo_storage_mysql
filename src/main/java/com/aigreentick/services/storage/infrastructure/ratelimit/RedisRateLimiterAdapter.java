package com.aigreentick.services.storage.infrastructure.ratelimit;

import com.aigreentick.services.storage.application.port.out.RateLimiterPort;
import com.aigreentick.services.storage.config.properties.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Distributed token bucket, evaluated atomically by a Lua script.
 *
 * <p>The predecessor kept buckets in a JVM-local map: with N replicas the effective
 * limit was N times the configured value, and whether a caller was throttled
 * depended on which pod the load balancer picked.
 *
 * <p>A hand-written script rather than a library: the whole behaviour is three
 * dozen lines, runs server-side in one round trip, and has no dependency whose
 * upgrade can silently change throttling semantics.
 *
 * <p>FAILS OPEN when Redis is unreachable (ADR-007).
 */
@Component
@Slf4j
public class RedisRateLimiterAdapter implements RateLimiterPort {

    /**
     * Refills lazily from the elapsed time since the last call, so no background
     * job is needed and an idle key simply expires.
     *
     * <p>Returns {@code {allowed, remaining, retryAfterMillis}}.
     */
    private static final String TOKEN_BUCKET_LUA = """
            local key        = KEYS[1]
            local capacity   = tonumber(ARGV[1])
            local refill     = tonumber(ARGV[2])
            local periodMs   = tonumber(ARGV[3])
            local requested  = tonumber(ARGV[4])
            local nowMs      = tonumber(ARGV[5])

            local state    = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens   = tonumber(state[1])
            local lastMs   = tonumber(state[2])

            if tokens == nil then
              tokens = capacity
              lastMs = nowMs
            end

            local elapsed = math.max(0, nowMs - lastMs)
            if elapsed > 0 and periodMs > 0 then
              tokens = math.min(capacity, tokens + (elapsed / periodMs) * refill)
            end

            local allowed = 0
            local retryAfterMs = 0
            if tokens >= requested then
              tokens = tokens - requested
              allowed = 1
            else
              local deficit = requested - tokens
              retryAfterMs = math.ceil((deficit / refill) * periodMs)
            end

            redis.call('HMSET', key, 'tokens', tokens, 'ts', nowMs)
            redis.call('PEXPIRE', key, math.max(periodMs * 2, 60000))
            return { allowed, math.floor(tokens), retryAfterMs }
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final RateLimitProperties properties;
    private final MeterRegistry meters;

    @SuppressWarnings("unchecked")
    public RedisRateLimiterAdapter(StringRedisTemplate redis, RateLimitProperties properties,
                                   MeterRegistry meters) {
        this.redis = redis;
        this.properties = properties;
        this.meters = meters;
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(TOKEN_BUCKET_LUA);
        redisScript.setResultType(List.class);
        this.script = redisScript;
    }

    @Override
    public Decision tryConsume(String key, long tokens, Limit limit) {
        if (!properties.enabled()) {
            return new Decision(true, limit.capacity(), limit.capacity(), Duration.ZERO, false);
        }
        try {
            List<?> result = redis.execute(script, List.of("ratelimit:" + key),
                    String.valueOf(limit.capacity()),
                    String.valueOf(limit.refillTokens()),
                    String.valueOf(limit.refillPeriod().toMillis()),
                    String.valueOf(tokens),
                    String.valueOf(System.currentTimeMillis()));

            if (result == null || result.size() < 3) {
                return failOpen(limit, "unexpected script result");
            }
            boolean allowed = asLong(result.get(0)) == 1L;
            long remaining = asLong(result.get(1));
            Duration retryAfter = Duration.ofMillis(asLong(result.get(2)));
            return new Decision(allowed, limit.capacity(), remaining, retryAfter, false);

        } catch (RuntimeException e) {
            return failOpen(limit, e.toString());
        }
    }

    private long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /**
     * Rejecting all traffic because the limiter is down converts a degradation into
     * an outage. The counter makes fail-open visible rather than silent.
     */
    private Decision failOpen(Limit limit, String cause) {
        meters.counter("storage.ratelimit.degraded").increment();
        if (!properties.failOpen()) {
            log.error("rate limiter unavailable and fail-open is disabled: {}", cause);
            return new Decision(false, limit.capacity(), 0, Duration.ofSeconds(5), true);
        }
        log.warn("rate limiter unavailable, failing open: {}", cause);
        return Decision.allowedDegraded(limit.capacity());
    }
}
