package com.apargo.services.storage.infrastructure.observability;

import com.apargo.services.storage.common.constants.ApiPaths;
import com.apargo.services.storage.common.constants.MdcKeys;
import com.apargo.services.storage.common.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sole owner of the request trace id. Runs FIRST, so every later filter — and
 * every log line, error response and audit event — carries it.
 *
 * <ul>
 *   <li>Public requests always get a NEW trace id. A {@code traceparent} sent by a
 *       client is ignored: the trace id is a backend identifier, and accepting one
 *       from outside would let any caller splice itself into, or collide with,
 *       another request's trace.</li>
 *   <li>{@code /internal/**} continues the caller's trace when it sends a
 *       well-formed {@code traceparent}, so one trace id follows the work across
 *       services.</li>
 *   <li>The trace id is returned as {@code meta.traceId}, never as a
 *       {@code traceparent} response header.</li>
 * </ul>
 *
 * <p>Ordering lives in {@code SecurityConfig} via
 * {@code FilterRegistrationBean#setOrder}; there is intentionally no {@code @Order}.
 */
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MdcKeys.TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Outermost filter: leave the pooled servlet thread with an empty MDC,
            // whatever inner code added and forgot to remove.
            MDC.clear();
        }
    }

    static String resolveTraceId(HttpServletRequest request) {
        if (isInternal(request)) {
            return TraceContext.traceIdFrom(request.getHeader(TraceContext.TRACEPARENT_HEADER))
                    .orElseGet(TraceContext::newTraceId);
        }
        return TraceContext.newTraceId();
    }

    private static boolean isInternal(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null
                && (path.equals(ApiPaths.INTERNAL) || path.startsWith(ApiPaths.INTERNAL + "/"));
    }
}
