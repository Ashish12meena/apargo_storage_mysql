package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code logging.request.*} — the per-request access log.
 *
 * <p>Deliberately placed under {@code logging.} rather than a namespace of its
 * own, so everything an operator can turn about logging is in one block of one
 * file. Spring Boot's own logging keys are a fixed set ({@code level},
 * {@code pattern}, {@code file}, {@code group}, {@code config},
 * {@code structured}, {@code charset}, {@code threshold}) and {@code request} is
 * not among them, so there is no collision.
 *
 * <p><b>Every field is live configuration.</b> Nothing here needs a redeploy:
 * these are read on each request, so changing a value and restarting is enough,
 * and the two booleans that carry real risk ({@code includeHeaders},
 * {@code includePayload}) both default to off.
 *
 * <h2>What is never logged, at any setting</h2>
 * The filter cannot be configured into leaking these, because it does not read
 * them at all: API keys, {@code Authorization} values, presigned URLs (the query
 * string carries the signature), storage keys, and response bodies. Header
 * capture is allowlist-only — {@link #headers()} names what may be recorded, and
 * anything absent from that list is not read. This mirrors the rule already
 * stated in {@code logback-prod.xml}.
 *
 * @param enabled       master switch. False and the filter is not registered at
 *                      all, so it costs nothing — not even a per-request branch.
 * @param logStart      emit a line when the request ARRIVES as well as when it
 *                      finishes. Off by default: it doubles log volume, and the
 *                      completion line already carries everything the start line
 *                      would. Worth turning on when requests are hanging and you
 *                      need to see what got in but never came out.
 * @param startLevel    level for the arrival line. DEBUG by default so
 *                      {@code logStart} alone is not enough to flood production
 *                      — it also takes a level change.
 * @param completeLevel level for the completion line on a 2xx/3xx. A 4xx logs a
 *                      rung higher and a 5xx logs at ERROR, always, regardless
 *                      of this setting.
 * @param slowThreshold a 2xx slower than this logs at WARN instead of
 *                      {@code completeLevel}. {@code PT0S} disables the
 *                      promotion. This is the setting that makes a latency
 *                      regression visible in the log without a dashboard.
 * @param includeQuery  append the query string to the logged path. On by
 *                      default; see {@link #maxQueryLength()} for the cap.
 * @param maxQueryLength truncate the query string beyond this many characters.
 *                      A cursor token is long, opaque and worth nothing in a log
 *                      line, and an unbounded query string is an easy way for a
 *                      caller to write arbitrary volume into your log storage.
 * @param includeClientIp record the TCP peer address. This is the peer, not
 *                      {@code X-Forwarded-For} — behind a proxy it is the
 *                      proxy's address. See {@code TraceContextFilter} for why
 *                      that header is not trusted.
 * @param includeHeaders record request headers named in {@link #headers()}.
 *                      OFF by default.
 * @param headers       the allowlist consulted when {@code includeHeaders} is
 *                      true. Matched case-insensitively. A header not on this
 *                      list is never read.
 * @param excludePaths  path PREFIXES that are not logged. Health probes and the
 *                      Swagger assets otherwise dominate the log and tell you
 *                      nothing; a liveness probe every second is 86,400 lines a
 *                      day of noise.
 */
@Validated
@ConfigurationProperties(prefix = "logging.request")
public record RequestLoggingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean logStart,
        @DefaultValue("DEBUG") String startLevel,
        @DefaultValue("INFO") String completeLevel,
        @DefaultValue("PT2S") Duration slowThreshold,
        @DefaultValue("true") boolean includeQuery,
        @DefaultValue("256") int maxQueryLength,
        @DefaultValue("true") boolean includeClientIp,
        @DefaultValue("false") boolean includeHeaders,
        @DefaultValue({"X-Request-Id", "X-Trace-Id", "Idempotency-Key",
                "Content-Type", "User-Agent"}) List<String> headers,
        @DefaultValue({"/actuator", "/swagger-ui", "/v3/api-docs", "/favicon.ico"})
        List<String> excludePaths) {

    /** Levels the two {@code *Level} settings may take. */
    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR, OFF }

    public RequestLoggingProperties {
        headers = headers == null ? List.of() : headers.stream()
                .map(String::trim).filter(h -> !h.isEmpty()).toList();
        excludePaths = excludePaths == null ? List.of() : excludePaths.stream()
                .map(String::trim).filter(p -> !p.isEmpty()).toList();
        slowThreshold = slowThreshold == null ? Duration.ZERO : slowThreshold;
    }

    /**
     * Parsed once at construction rather than per request.
     *
     * <p>An unrecognised value falls back rather than failing the boot. Logging
     * configuration is not worth refusing to start over, and a typo'd level that
     * silently downgrades a line is a far smaller problem than a service that
     * will not come up.
     */
    public Level startLevelOrDefault() {
        return parse(startLevel, Level.DEBUG);
    }

    /** @see #startLevelOrDefault() */
    public Level completeLevelOrDefault() {
        return parse(completeLevel, Level.INFO);
    }

    private static Level parse(String raw, Level fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Level.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Lower-cased allowlist, for a case-insensitive header lookup. */
    public Set<String> headerAllowlist() {
        return headers.stream().map(h -> h.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** True when {@code slow-threshold} is set to something that can fire. */
    public boolean slowDetectionEnabled() {
        return !slowThreshold.isZero() && !slowThreshold.isNegative();
    }
}
