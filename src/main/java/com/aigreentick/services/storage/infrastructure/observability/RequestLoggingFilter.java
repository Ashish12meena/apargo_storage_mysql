package com.aigreentick.services.storage.infrastructure.observability;

import com.aigreentick.services.storage.config.properties.RequestLoggingProperties;
import com.aigreentick.services.storage.config.properties.RequestLoggingProperties.Level;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The access log: one line per request, carrying method, path, status and
 * duration.
 *
 * <p>This is the piece that makes a request TRACKABLE end to end. The MDC set by
 * {@link TraceContextFilter} already stamps {@code traceId} and {@code requestId}
 * on every line the service emits; this filter is what guarantees there is at
 * least one line per request even when nothing else logs, and that the line
 * records how the request actually ended. Given a trace id from a caller's error
 * response, a single grep now returns the whole story of that request.
 *
 * <h2>Position in the chain</h2>
 * Registered at order 2 in {@code SecurityConfig} — immediately after
 * {@link TraceContextFilter} and BEFORE authentication and rate limiting. That
 * placement is the point:
 * <ul>
 *   <li>The MDC already holds {@code traceId} and {@code requestId}, so the
 *       access line correlates with everything else.</li>
 *   <li>Requests REJECTED by authentication (401) or the rate limiter (429) are
 *       still logged. A filter placed after them would log only the traffic that
 *       got through, which is exactly the traffic you least need to see.</li>
 *   <li>The completion line is written on the way back OUT, after
 *       {@code TenantContextFilter} has run, so {@code orgId} and
 *       {@code projectId} are in the MDC by then even though they did not exist
 *       when the request arrived. ({@code TenantContextFilter} clears the
 *       ThreadLocal in its {@code finally}, but the MDC is cleared later still,
 *       by {@code TraceContextFilter}.)</li>
 * </ul>
 *
 * <h2>What this filter deliberately does not do</h2>
 * <b>It never wraps the request or the response.</b> Body capture would mean
 * buffering, and {@code /api/v1/media/serve} streams files of up to the
 * multipart ceiling straight to the client — buffering those to produce a log
 * line would turn a constant-memory download into a heap-sized one, and would
 * break range requests. So there is no {@code ContentCachingResponseWrapper}
 * here and no {@code include-payload} switch to turn one on. Response size is
 * not logged for the same reason: knowing it requires counting the bytes
 * through a wrapper.
 *
 * <p>It also never logs credentials. API keys, {@code Authorization} and
 * presigned URLs are not read at any setting; header capture is allowlist-only
 * (see {@link RequestLoggingProperties#headers()}).
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** MDC keys added for the completion line only, then removed. */
    private static final String MDC_STATUS = "httpStatus";
    private static final String MDC_DURATION = "durationMs";
    private static final String MDC_METHOD = "httpMethod";
    private static final String MDC_PATH = "httpPath";

    private final RequestLoggingProperties properties;
    private final Set<String> headerAllowlist;

    public RequestLoggingFilter(RequestLoggingProperties properties) {
        this.properties = properties;
        this.headerAllowlist = properties.headerAllowlist();
    }

    /**
     * Health probes and API-doc assets are skipped by prefix. A liveness probe
     * on a one-second interval is 86,400 lines a day that describe nothing.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : properties.excludePaths()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = describePath(request);
        long startNanos = System.nanoTime();

        Level startLevel = properties.startLevelOrDefault();
        if (properties.logStart() && startLevel != Level.OFF) {
            emit(startLevel).log("--> {} {}{}", method, path, startDetail(request));
        }

        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // Recorded so the access line says the request blew up rather than
            // reporting whatever status happened to be on the response object.
            // Rethrown unchanged — this filter observes, it does not handle.
            failure = e;
            throw e;
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = failure != null ? 500 : response.getStatus();
            logCompletion(method, path, status, millis, request, failure);
        }
    }

    private void logCompletion(String method, String path, int status, long millis,
                               HttpServletRequest request, Throwable failure) {
        Level level = levelFor(status, millis);
        if (level == Level.OFF) {
            return;
        }

        // Put the structured fields on the MDC so a JSON appender emits them as
        // real fields rather than leaving them buried in the message text, which
        // is what makes "every request over 2s yesterday" a query instead of a
        // regex. Removed immediately: the MDC outlives this filter by one frame
        // (TraceContextFilter clears it), and these values are meaningless on
        // any other line.
        MDC.put(MDC_METHOD, method);
        MDC.put(MDC_PATH, path);
        MDC.put(MDC_STATUS, Integer.toString(status));
        MDC.put(MDC_DURATION, Long.toString(millis));
        try {
            // Message only for a failure, never the stack trace:
            // GlobalExceptionHandler owns the stack trace for anything that
            // reaches it, and logging it twice doubles the noise on every 500.
            emit(level).log("<-- {} {} {} {}ms{}", method, path, status, millis,
                    completionDetail(request, failure));
        } finally {
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_STATUS);
            MDC.remove(MDC_DURATION);
        }
    }

    /**
     * Status and latency decide the level, not configuration alone.
     *
     * <p>A 5xx is ERROR and a 4xx is WARN whatever {@code complete-level} says,
     * because the reason to lower {@code complete-level} is to quieten routine
     * successful traffic — and if that also hid the failures, the setting would
     * be unusable in production and nobody would touch it. Turning failures off
     * entirely stays possible, but it takes {@code OFF} explicitly.
     */
    private Level levelFor(int status, long millis) {
        Level configured = properties.completeLevelOrDefault();
        if (configured == Level.OFF) {
            return Level.OFF;
        }
        if (status >= 500) {
            return Level.ERROR;
        }
        if (status >= 400) {
            return Level.WARN;
        }
        if (properties.slowDetectionEnabled() && millis >= properties.slowThreshold().toMillis()) {
            return Level.WARN;
        }
        return configured;
    }

    /**
     * Fluent builder at the configured level.
     *
     * <p>{@code OFF} is filtered out by both callers before they get here, so it
     * cannot reach this switch. It still needs an arm — the switch is exhaustive
     * over the enum — and {@code atTrace()} is the safe one to give it: if the
     * guard above were ever removed, the failure mode is a line nobody sees
     * rather than a {@code MatchException} thrown out of a servlet filter.
     */
    private LoggingEventBuilder emit(Level level) {
        return switch (level) {
            case TRACE, OFF -> log.atTrace();
            case DEBUG -> log.atDebug();
            case INFO -> log.atInfo();
            case WARN -> log.atWarn();
            case ERROR -> log.atError();
        };
    }

    /**
     * Path plus query string, with the query truncated.
     *
     * <p>The cap is not cosmetic. A keyset cursor is a long opaque blob that
     * reads as line noise, and without a ceiling any caller can write unbounded
     * volume into your log storage by padding a query parameter.
     */
    private String describePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        if (!properties.includeQuery() || query == null || query.isEmpty()) {
            return path;
        }
        int cap = Math.max(0, properties.maxQueryLength());
        if (cap == 0) {
            return path;
        }
        if (query.length() > cap) {
            query = query.substring(0, cap) + "...";
        }
        return path + "?" + query;
    }

    /** Arrival-line extras: content type and declared length. */
    private String startDetail(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        String contentType = request.getContentType();
        if (contentType != null) {
            // Strip parameters: a multipart boundary is a random token that
            // changes every request and defeats log grouping.
            int semicolon = contentType.indexOf(';');
            sb.append(" type=").append(semicolon < 0 ? contentType : contentType.substring(0, semicolon));
        }
        long declared = request.getContentLengthLong();
        if (declared >= 0) {
            sb.append(" bytes=").append(declared);
        }
        return sb.toString();
    }

    /** Completion-line extras: client address, allowlisted headers, error class. */
    private String completionDetail(HttpServletRequest request, Throwable failure) {
        StringBuilder sb = new StringBuilder();
        if (properties.includeClientIp()) {
            sb.append(" ip=").append(request.getRemoteAddr());
        }
        if (properties.includeHeaders() && !headerAllowlist.isEmpty()) {
            Map<String, String> captured = capturedHeaders(request);
            if (!captured.isEmpty()) {
                sb.append(" headers=").append(captured);
            }
        }
        if (failure != null) {
            sb.append(" error=").append(failure.getClass().getSimpleName())
                    .append('(').append(failure.getMessage()).append(')');
        }
        return sb.toString();
    }

    /**
     * Allowlist-only header capture.
     *
     * <p>Iterates the REQUEST's headers and keeps the ones named in
     * configuration, rather than iterating the configured names and fetching
     * each. Same result, but this way an operator adding {@code Authorization}
     * to the allowlist still cannot extract a value the filter never reads —
     * the check below rejects it before {@code getHeader} is called.
     */
    private Map<String, String> capturedHeaders(HttpServletRequest request) {
        Map<String, String> captured = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return captured;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!headerAllowlist.contains(lower) || isSecret(lower)) {
                continue;
            }
            captured.put(name, request.getHeader(name));
        }
        return captured;
    }

    /**
     * Credential headers, never logged.
     *
     * <p>A hard floor under the allowlist, not a second allowlist. Configuration
     * can narrow what is captured; it cannot widen it to include these.
     */
    private boolean isSecret(String lowerCaseName) {
        return lowerCaseName.equals("authorization")
                || lowerCaseName.equals("x-api-key")
                || lowerCaseName.equals("cookie")
                || lowerCaseName.equals("set-cookie")
                || lowerCaseName.equals("proxy-authorization");
    }
}
