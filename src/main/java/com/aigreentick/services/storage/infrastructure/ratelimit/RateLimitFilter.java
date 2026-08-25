package com.aigreentick.services.storage.infrastructure.ratelimit;

import com.aigreentick.services.storage.api.error.ErrorResponseWriter;
import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.api.security.TenantPrincipal;
import com.aigreentick.services.storage.application.port.out.RateLimiterPort;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.config.properties.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Layered limiting so one tenant cannot exhaust the service and one user cannot
 * exhaust their tenant.
 *
 * <p>Runs AFTER authentication, so limits key on a verified tenant rather than a
 * spoofable header. Unauthenticated traffic falls back to a per-IP bucket.
 *
 * <p>{@code /serve/**} is covered explicitly. The predecessor's limiter recognised
 * only {@code /upload} and {@code /media/{id}}, so the one route that streams
 * unbounded bytes off disk fell to a shared default bucket.
 *
 * <p>Ordering lives in {@code SecurityConfig} via
 * {@code FilterRegistrationBean#setOrder}, which is authoritative for
 * registration-bean filters — an {@code @Order} here would be silently ignored.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final RateLimiterPort.Limit FALLBACK =
            new RateLimiterPort.Limit(100, 100, Duration.ofMinutes(1));

    private final RateLimiterPort limiter;
    private final RateLimitProperties properties;
    private final ErrorResponseWriter errorWriter;
    private final MeterRegistry meters;

    public RateLimitFilter(RateLimiterPort limiter, RateLimitProperties properties,
                           ErrorResponseWriter errorWriter, MeterRegistry meters) {
        this.limiter = limiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.meters = meters;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Batch upload is NOT rate limited. It is a service-to-service path
        // reached only by an authenticated internal caller, and it is already
        // bounded by two harder limits that a token bucket would only duplicate:
        // storage.max-files-per-batch caps files per request, and the multipart
        // max-request-size caps bytes per request.
        //
        // A per-caller bucket here was actively wrong, not merely redundant. The
        // only identity an API-key caller carries is the CLIENT id, so every
        // organisation and project sharing that client shared one bucket — one
        // busy tenant would have throttled every other tenant's uploads. Keying
        // per org/project instead would not fix it either, since the point of
        // this endpoint is that one upstream service submits work on behalf of
        // all of them.
        //
        // The tenant-facing single-file routes remain limited; only this path is
        // exempt.
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith(ApiPaths.MEDIA_UPLOAD_BATCH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        String ruleName = resolveRule(request);
        RateLimiterPort.Limit limit = toLimit(properties.rule(ruleName, null));
        String key = resolveKey(request, ruleName);

        RateLimiterPort.Decision decision = limiter.tryConsume(key, 1, limit);

        response.setHeader(HeaderNames.RATELIMIT_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HeaderNames.RATELIMIT_REMAINING, String.valueOf(Math.max(0, decision.remaining())));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
            response.setHeader(HeaderNames.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.setHeader(HeaderNames.RATELIMIT_RESET, String.valueOf(retryAfterSeconds));
            meters.counter("storage.ratelimit.rejected", "rule", ruleName).increment();
            errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    ErrorCode.RATE_LIMITED, RequestContext.traceIdOrNull());
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimiterPort.Limit toLimit(RateLimitProperties.Rule rule) {
        return rule == null ? FALLBACK
                : new RateLimiterPort.Limit(rule.capacity(), rule.refillTokens(), rule.refillPeriod());
    }

    private String resolveRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith(ApiPaths.MEDIA_SERVE)) {
            return "serve-per-project";
        }
        // No branch for /media/upload/batch: it never reaches here, because
        // shouldNotFilter exempts it. Note the ordering hazard if that is ever
        // reversed — /api/v1/media/upload/batch starts with /api/v1/media/upload,
        // so it would silently fall into upload-per-user and a twenty-file batch
        // would spend a single token.
        if (path.startsWith(ApiPaths.MEDIA + "/upload") || path.startsWith(ApiPaths.MEDIA + "/uploads")) {
            return "upload-per-user";
        }
        if ("GET".equals(method)) {
            return "read-per-project";
        }
        return "read-per-project";
    }

    /**
     * Keys on the VERIFIED tenant where available. Anonymous traffic keys on the
     * client IP as the TCP peer address — the predecessor took
     * {@code X-Forwarded-For} verbatim, so any caller could spoof a fresh bucket.
     */
    private String resolveKey(HttpServletRequest request, String ruleName) {
        TenantPrincipal principal = TenantContext.getOrNull();
        if (principal == null) {
            var ctx = RequestContext.get();
            String ip = ctx == null ? request.getRemoteAddr() : ctx.clientIp();
            return "anon:" + ip;
        }
        if (ruleName.endsWith("per-user") && principal.userId() != null) {
            return "u:" + principal.userId() + ":" + ruleName;
        }
        return "p:" + principal.orgId() + ":" + principal.projectId() + ":" + ruleName;
    }
}