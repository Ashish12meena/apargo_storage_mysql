package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.api.error.ErrorResponseWriter;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.config.properties.SecurityProperties;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.aigreentick.services.storage.infrastructure.observability.TraceContextFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Guards {@code /internal/**}. Requires an API key whose client holds
 * {@code quota:admin}.
 *
 * <p>In the predecessor this surface had NO authentication whatsoever, and sat
 * outside the rate limiter. A single unauthenticated {@code PUT /internal/quota/org}
 * set any tenant's limit to zero — a trivial denial of service against every
 * tenant, from anywhere on the network.
 *
 * <p>The application check is one layer. In a real deployment it is backed by a
 * NetworkPolicy restricting the route to the organisation-service pod selector.
 * There is no flag that disables this beyond the global
 * {@code security.api-key-enabled}, which production rejects.
 *
 * <p>Ordering lives in {@code SecurityConfig} via
 * {@code FilterRegistrationBean#setOrder}, which is authoritative for
 * registration-bean filters — an {@code @Order} here would be silently ignored.
 */
@Slf4j
public class InternalCallerFilter extends OncePerRequestFilter {

    /** Placeholder tenant: internal calls name their target in the request body. */
    private static final TenantRef ADMIN_SCOPE = new TenantRef(1L, 1L);

    private final ApiKeyAuthenticator authenticator;
    private final SecurityProperties properties;
    private final ErrorResponseWriter errorWriter;
    private final MeterRegistry meters;

    public InternalCallerFilter(ApiKeyAuthenticator authenticator, SecurityProperties properties,
                                ErrorResponseWriter errorWriter, MeterRegistry meters) {
        this.authenticator = authenticator;
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.meters = meters;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith(ApiPaths.INTERNAL) || path.startsWith(ApiPaths.LEGACY_QUOTA));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            if (!authenticator.enabled()) {
                TenantContext.set(new TenantPrincipal(ADMIN_SCOPE, "auth-disabled",
                        java.util.EnumSet.allOf(Scope.class), null, Actor.ActorType.SERVICE, true));
                TraceContextFilter.enrichMdc();
                chain.doFilter(request, response);
                return;
            }

            Optional<ApiKeyAuthenticator.ResolvedClient> client =
                    authenticator.authenticate(request.getHeader(properties.apiKeyHeader()));

            if (client.isEmpty()) {
                reject(response, "missing_or_unknown_api_key", request);
                return;
            }
            if (!client.get().scopes().contains(Scope.QUOTA_ADMIN)) {
                reject(response, "insufficient_scope", request);
                return;
            }

            TenantContext.set(new TenantPrincipal(ADMIN_SCOPE, client.get().definition().id(),
                    client.get().scopes(), null, Actor.ActorType.SERVICE, false));
            // The caller id is the only identity an internal call has; without
            // this it never reaches a log line.
            TraceContextFilter.enrichMdc();
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, String reason, HttpServletRequest request)
            throws IOException {
        meters.counter("storage.auth.rejected", "reason", reason, "surface", "internal").increment();
        log.warn("internal API call rejected ({}) path={} remote={}",
                reason, request.getRequestURI(), request.getRemoteAddr());
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.UNAUTHENTICATED, RequestContext.traceIdOrNull());
    }
}
