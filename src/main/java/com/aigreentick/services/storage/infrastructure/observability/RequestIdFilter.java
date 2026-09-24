package com.aigreentick.services.storage.infrastructure.observability;

import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.api.security.TenantPrincipal;
import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.context.RequestContextData;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes the request id and MDC. Runs FIRST, so even an authentication
 * rejection carries an id the caller can quote.
 *
 * <p>{@code X-Request-Id} is the ONLY tracking header (API Standard §1). A
 * well-formed incoming value is reused — template-service passes on the id of
 * the request that triggered it, so one id follows the work across services —
 * otherwise one is generated. It is echoed on every response, including 204s
 * and rejections, and returned as {@code meta.requestId}. The pre-standard
 * {@code X-Trace-Id} is no longer read or written.
 *
 * <p>Named {@code RequestIdFilter}, not {@code RequestContextFilter}: Spring
 * registers its own {@code requestContextFilter} bean and a same-named bean
 * fails the context at startup.
 *
 * <p>Ordering lives in {@code SecurityConfig} via
 * {@code FilterRegistrationBean#setOrder} — there is intentionally no
 * {@code @Order} here.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    /** UUIDs and similar opaque ids; nothing that can forge or bloat a log line. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final int MAX_USER_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HeaderNames.REQUEST_ID));
        String userId = cap(request.getHeader(HeaderNames.USER_ID));

        RequestContext.set(new RequestContextData(requestId, userId, clientIp(request), Instant.now()));
        MDC.put("requestId", requestId);
        if (userId != null) {
            MDC.put("userId", userId);
        }
        response.setHeader(HeaderNames.REQUEST_ID, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.clear();
        }
    }

    static String resolveRequestId(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    private static String cap(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_USER_ID_LENGTH ? trimmed.substring(0, MAX_USER_ID_LENGTH) : trimmed;
    }

    /**
     * The TCP peer address, always. {@code X-Forwarded-For} is deliberately NOT
     * consulted: any caller can set it, and the address keys the rate-limit
     * bucket and the audit trail. Behind a proxy this is the proxy; resolve the
     * real address at the ingress if per-caller granularity is needed.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /**
     * Adds tenant identity to the MDC once authentication has resolved it.
     * {@code caller} is the authenticated client (e.g. {@code template-service});
     * {@code userId} is the acting user from {@code X-User-Id}, set above.
     */
    public static void enrichMdc() {
        TenantPrincipal principal = TenantContext.getOrNull();
        if (principal != null) {
            MDC.put("orgId", String.valueOf(principal.orgId()));
            MDC.put("projectId", String.valueOf(principal.projectId()));
            if (principal.userId() != null) {
                MDC.put("caller", principal.userId());
            }
        }
    }
}
