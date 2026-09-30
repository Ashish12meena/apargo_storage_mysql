package com.apargo.services.storage.infrastructure.observability;

import com.apargo.services.storage.api.security.TenantContext;
import com.apargo.services.storage.api.security.TenantPrincipal;
import com.apargo.services.storage.common.constants.ApiPaths;
import com.apargo.services.storage.common.constants.HeaderNames;
import com.apargo.services.storage.common.constants.MdcKeys;
import com.apargo.services.storage.common.context.RequestContext;
import com.apargo.services.storage.common.context.RequestContextData;
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
 * Establishes the request id and the request MDC ({@code requestId},
 * {@code userId}, {@code internalCaller}, and — once authentication resolves
 * them — {@code orgId}, {@code projectId}, {@code caller}). Runs directly after
 * {@code TraceContextFilter}, so even an authentication rejection carries ids the
 * caller can quote.
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
    private static final int MAX_CALLER_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HeaderNames.REQUEST_ID));
        String userId = cap(request.getHeader(HeaderNames.USER_ID));

        RequestContext.set(new RequestContextData(requestId, userId, clientIp(request), Instant.now()));
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        if (userId != null) {
            MDC.put(MdcKeys.USER_ID, userId);
        }
        if (isInternal(request)) {
            // Declared caller of an internal call. Replaced by the authenticated
            // client id once InternalCallerFilter has verified the key.
            String declaredCaller = cap(request.getHeader(HeaderNames.INTERNAL_CALLER), MAX_CALLER_LENGTH);
            if (declaredCaller != null && VALID_ID.matcher(declaredCaller).matches()) {
                MDC.put(MdcKeys.INTERNAL_CALLER, declaredCaller);
            }
        }
        response.setHeader(HeaderNames.REQUEST_ID, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            // Only what this filter owns. The trace id belongs to TraceContextFilter,
            // the outermost filter, which clears the whole MDC on its way out.
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.INTERNAL_CALLER);
            MDC.remove(MdcKeys.ORG_ID);
            MDC.remove(MdcKeys.PROJECT_ID);
            MDC.remove(MdcKeys.CALLER);
        }
    }

    static String resolveRequestId(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    private static String cap(String value) {
        return cap(value, MAX_USER_ID_LENGTH);
    }

    private static String cap(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static boolean isInternal(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null
                && (path.equals(ApiPaths.INTERNAL) || path.startsWith(ApiPaths.INTERNAL + "/"));
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
            MDC.put(MdcKeys.ORG_ID, String.valueOf(principal.orgId()));
            MDC.put(MdcKeys.PROJECT_ID, String.valueOf(principal.projectId()));
            if (principal.userId() != null) {
                MDC.put(MdcKeys.CALLER, principal.userId());
            }
        }
    }

    /**
     * Records the AUTHENTICATED client as the internal caller, replacing the
     * declared {@code X-Internal-Caller} value. Called by
     * {@code InternalCallerFilter} after the API key has been verified, so the
     * audit actor of an internal call is the service the key belongs to.
     */
    public static void markInternalCaller(String authenticatedClientId) {
        if (authenticatedClientId != null && !authenticatedClientId.isBlank()) {
            MDC.put(MdcKeys.INTERNAL_CALLER, authenticatedClientId);
        }
    }
}
