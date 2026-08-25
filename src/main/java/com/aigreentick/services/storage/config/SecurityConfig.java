package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.api.error.ErrorResponseWriter;
import com.aigreentick.services.storage.api.security.ApiKeyAuthenticator;
import com.aigreentick.services.storage.api.security.InternalCallerFilter;
import com.aigreentick.services.storage.api.security.TenantContextFilter;
import com.aigreentick.services.storage.config.properties.RateLimitProperties;
import com.aigreentick.services.storage.config.properties.RequestLoggingProperties;
import com.aigreentick.services.storage.config.properties.SecurityProperties;
import com.aigreentick.services.storage.infrastructure.observability.RequestLoggingFilter;
import com.aigreentick.services.storage.infrastructure.observability.TraceContextFilter;
import com.aigreentick.services.storage.infrastructure.ratelimit.RateLimitFilter;
import com.aigreentick.services.storage.application.port.out.RateLimiterPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filter chain, in order:
 *
 * <pre>
 *   TraceContextFilter     trace id + MDC, so even a 401 carries a trace id
 *   RequestLoggingFilter   access log; wraps the three below so rejections log
 *   InternalCallerFilter   guards /internal/**
 *   TenantContextFilter    verified principal for /api/**
 *   RateLimitFilter        keys on the verified tenant, not a spoofable header
 * </pre>
 *
 * <p>The access log sits at order 2 rather than last on purpose. From there it
 * wraps authentication and rate limiting, so a 401 or a 429 produces an access
 * line like any other request — traffic that never got through is precisely the
 * traffic worth seeing. It reads the response status on the way back out, by
 * which point {@code TenantContextFilter} has put {@code orgId} and
 * {@code projectId} on the MDC.
 *
 * <p>Orders 3–5 below were 2–4 before the access log was inserted. Only the
 * relative sequence matters to {@code FilterRegistrationBean}; the absolute
 * numbers carry no meaning and the chain is unchanged apart from the addition.
 *
 * <p>Servlet filters, not Spring Security: the design in docs/09 requires running
 * before the dispatcher and covering error dispatches, which HandlerInterceptors
 * do not.
 *
 * <p>Authentication is a shared API key per calling service (ADR-010, revised).
 * {@code ApiKeyAuthenticator} is the seam — swapping back to token verification
 * later replaces that one class and nothing else.
 *
 * <p>Bean naming convention: every method here returns a
 * {@link FilterRegistrationBean}, not the filter itself, and is therefore named
 * {@code <filter>Registration}. The suffix is accurate and it keeps these bean
 * names out of the namespace Spring Boot's autoconfiguration registers filters
 * under — a bare {@code requestContextFilter} previously clashed with
 * {@code WebMvcAutoConfiguration} and failed the context at startup.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<TraceContextFilter> traceContextFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new TraceContextFilter());
        registration.setOrder(1);
        return registration;
    }

    /**
     * The access log. {@code @ConditionalOnProperty} rather than a branch inside
     * the filter: with {@code logging.request.enabled=false} the bean is never
     * created, so the filter is not in the chain at all and costs nothing per
     * request. {@code matchIfMissing} keeps it on by default — a service where
     * you cannot see the requests is not a state anyone should arrive at by
     * omission.
     */
    @Bean
    @ConditionalOnProperty(prefix = "logging.request", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
            RequestLoggingProperties properties) {
        var registration = new FilterRegistrationBean<>(new RequestLoggingFilter(properties));
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InternalCallerFilter> internalCallerFilterRegistration(
            ApiKeyAuthenticator authenticator, SecurityProperties properties,
            ErrorResponseWriter errorWriter, MeterRegistry meters) {
        var registration = new FilterRegistrationBean<>(
                new InternalCallerFilter(authenticator, properties, errorWriter, meters));
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            ApiKeyAuthenticator authenticator, SecurityProperties properties,
            ErrorResponseWriter errorWriter, MeterRegistry meters) {
        var registration = new FilterRegistrationBean<>(
                new TenantContextFilter(authenticator, properties, errorWriter, meters));
        registration.setOrder(4);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimiterPort limiter, RateLimitProperties properties,
            ErrorResponseWriter errorWriter, MeterRegistry meters) {
        var registration = new FilterRegistrationBean<>(
                new RateLimitFilter(limiter, properties, errorWriter, meters));
        registration.setOrder(5);
        return registration;
    }
}