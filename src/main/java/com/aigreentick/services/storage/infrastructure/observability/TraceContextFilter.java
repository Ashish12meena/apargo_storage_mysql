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

/**
 * Establishes the trace id and MDC. Runs FIRST, so even an authentication
 * rejection carries a trace id the caller can quote.
 *
 * <p>Named {@code TraceContextFilter} deliberately: Spring ships its own
 * {@code org.springframework.web.filter.RequestContextFilter}, registered by
 * {@code WebMvcAutoConfiguration} under the bean name {@code requestContextFilter}.
 * Reusing that name here collided with the framework bean at startup and, if
 * overriding were enabled, would have suppressed the framework's filter and
 * silently broken {@code RequestContextHolder} / {@code @RequestScope}.
 *
 * <p>Ordering lives in {@code SecurityConfig} via
 * {@code FilterRegistrationBean#setOrder}, which is authoritative for
 * registration-bean filters — there is intentionally no {@code @Order} here, so
 * there is a single source of truth for filter order.
 *
 * <p>None of this existed in the predecessor: the trace id was generated locally,
 * never propagated, and never reached a log line or an error body.
 */
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = firstNonBlank(request.getHeader(HeaderNames.TRACE_ID),
                UUID.randomUUID().toString().replace("-", ""));
        String requestId = firstNonBlank(request.getHeader(HeaderNames.REQUEST_ID),
                UUID.randomUUID().toString());

        RequestContext.set(new RequestContextData(traceId, requestId, clientIp(request), Instant.now()));
        MDC.put("traceId", traceId);
        MDC.put("requestId", requestId);
        response.setHeader(HeaderNames.TRACE_ID, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Tenant MDC is added after authentication resolves, so it is read
            // back here rather than at the top of the filter.
            RequestContext.clear();
            MDC.clear();
        }
    }

    /**
     * The TCP peer address, always.
     *
     * <p>{@code X-Forwarded-For} is deliberately NOT consulted. It is a header any
     * caller can set to any value, and the resolved address is used as the
     * rate-limit bucket key and written to the audit trail — so honouring it
     * unconditionally would let a caller mint a fresh bucket on every request by
     * varying the header, and write chosen addresses into the audit log. That was
     * the predecessor's behaviour.
     *
     * <p>The trusted-proxy CIDR allowlist that previously gated this was removed
     * on the grounds that it is deployment configuration the service should not
     * carry. The consequence is that behind a proxy every request keys to the
     * proxy's address rather than the caller's: rate limiting degrades to
     * per-proxy, and the audit trail records the proxy. That is a loss of
     * granularity, not a loss of safety. If per-caller granularity is needed
     * later, resolve the real address at the ingress and pass it as a VERIFIED
     * value rather than trusting a client-settable header here.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /** Adds tenant identity to the MDC once authentication has resolved it. */
    public static void enrichMdc() {
        TenantPrincipal principal = TenantContext.getOrNull();
        if (principal != null) {
            MDC.put("orgId", String.valueOf(principal.orgId()));
            MDC.put("projectId", String.valueOf(principal.projectId()));
            if (principal.userId() != null) {
                MDC.put("userId", principal.userId());
            }
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}