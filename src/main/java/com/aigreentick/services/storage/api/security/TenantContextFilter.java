package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.api.error.ErrorResponseWriter;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.aigreentick.services.storage.infrastructure.observability.RequestIdFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates the caller by API key, then establishes the
 * {@link TenantPrincipal} from that caller plus the tenant headers.
 *
 * <p>A servlet {@code Filter}, not a {@code HandlerInterceptor}: interceptors run
 * inside the dispatcher, so they are skipped on error dispatches and on requests
 * that never reach a handler. The predecessor used an interceptor AND excluded
 * {@code /api/v1/media/serve/**} from it, so the file-serving path had no tenant
 * context at all.
 *
 * <p>What this model does and does not give you:
 * <ul>
 *   <li><b>Does:</b> only holders of a configured key can reach the API. An
 *       arbitrary caller on the network can no longer act as any tenant, which is
 *       the predecessor's central defect.</li>
 *   <li><b>Does not:</b> stop an authenticated caller asserting a tenant that is
 *       not theirs. Pin a client to a fixed tenant
 *       ({@code security.clients[].fixed-org-id}) where that matters.</li>
 * </ul>
 *
 * <p>Ordering lives in {@code SecurityConfig} via
 * {@code FilterRegistrationBean#setOrder}, which is authoritative for
 * registration-bean filters. There is deliberately no {@code @Order} here: it
 * would be silently ignored, so editing it to reorder the chain would appear to
 * do nothing.
 */
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticator authenticator;
    private final ErrorResponseWriter errorWriter;
    private final MeterRegistry meters;

    public TenantContextFilter(ApiKeyAuthenticator authenticator,
                               ErrorResponseWriter errorWriter, MeterRegistry meters) {
        this.authenticator = authenticator;
        this.errorWriter = errorWriter;
        this.meters = meters;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith(ApiPaths.INTERNAL)      // handled by InternalCallerFilter
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Resolution resolution = resolve(request);
            if (resolution.principal() == null) {
                // 401 when the caller is not authenticated; 400 when it is but the
                // tenant headers are missing or unusable (API Standard §6: a
                // header that can't be read is BAD_REQUEST, not an auth failure).
                if (resolution.failure() == ErrorCode.BAD_REQUEST) {
                    errorWriter.write(request, response, ErrorCode.BAD_REQUEST,
                            "Missing or invalid header '" + HeaderNames.ORG_ID + "' or '"
                                    + HeaderNames.PROJECT_ID + "'");
                } else {
                    errorWriter.write(request, response, resolution.failure());
                }
                return;
            }
            TenantContext.set(resolution.principal());
            // Tenant identity reaches the MDC only here, because it does not
            // exist until authentication resolves. Without this call no log line
            // in the service carries an org or project, which makes a
            // cross-tenant incident unattributable after the fact.
            RequestIdFilter.enrichMdc();
            chain.doFilter(request, response);
        } finally {
            // finally, not afterCompletion: context must not survive an exception
            // path onto the next request served by this pooled thread.
            TenantContext.clear();
        }
    }

    /** Either a principal, or the error code explaining why there is none. */
    private record Resolution(TenantPrincipal principal, ErrorCode failure) {

        static Resolution of(Optional<TenantPrincipal> principal, ErrorCode failure) {
            return principal.map(p -> new Resolution(p, null)).orElseGet(() -> new Resolution(null, failure));
        }
    }

    private Resolution resolve(HttpServletRequest request) {
        Long orgId = parseLong(request.getHeader(HeaderNames.ORG_ID));
        Long projectId = parseLong(request.getHeader(HeaderNames.PROJECT_ID));

        if (!authenticator.enabled()) {
            // Development only. StartupAssertions fails the boot if this is false
            // under the prod profile.
            return Resolution.of(authenticator.anonymousDevPrincipal(orgId, projectId), ErrorCode.BAD_REQUEST);
        }

        String presented = request.getHeader(HeaderNames.INTERNAL_API_KEY);
        Optional<ApiKeyAuthenticator.ResolvedClient> client = authenticator.authenticate(presented);
        if (client.isEmpty()) {
            meters.counter("storage.auth.rejected",
                    "reason", presented == null ? "missing_api_key" : "unknown_api_key").increment();
            log.warn("rejected request to {} from {}: {}", request.getRequestURI(),
                    request.getRemoteAddr(), presented == null ? "no API key" : "unrecognised API key");
            return new Resolution(null, ErrorCode.UNAUTHENTICATED);
        }
        authenticator.checkDeclaredCaller(client.get(), request.getHeader(HeaderNames.INTERNAL_CALLER));

        Optional<TenantPrincipal> principal =
                authenticator.toPrincipal(client.get(), orgId, projectId);
        if (principal.isEmpty()) {
            meters.counter("storage.auth.rejected", "reason", "missing_or_invalid_tenant").increment();
            log.warn("client {} supplied no usable tenant headers", client.get().definition().id());
        }
        return Resolution.of(principal, ErrorCode.BAD_REQUEST);
    }

    /**
     * Never throws. The predecessor called {@code Long.valueOf} unguarded inside an
     * interceptor, so a non-numeric header produced a 500. It is now a 400.
     */
    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
