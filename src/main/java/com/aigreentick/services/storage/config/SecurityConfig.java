package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.api.error.ErrorResponseWriter;
import com.aigreentick.services.storage.api.security.ApiKeyAuthenticator;
import com.aigreentick.services.storage.api.security.InternalCallerFilter;
import com.aigreentick.services.storage.api.security.TenantContextFilter;
import com.aigreentick.services.storage.config.properties.RateLimitProperties;
import com.aigreentick.services.storage.config.properties.RequestLoggingProperties;
import com.aigreentick.services.storage.infrastructure.observability.RequestLoggingFilter;
import com.aigreentick.services.storage.infrastructure.observability.RequestIdFilter;
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
 *   1 RequestIdFilter        X-Request-Id + MDC, so even a 401 carries a request id
 *   2 RequestLoggingFilter   access log; wraps the three below so rejections log
 *   3 InternalCallerFilter   guards /internal/**
 *   4 TenantContextFilter    authenticated principal for /api/**
 *   5 RateLimitFilter        keys on the authenticated tenant, not a spoofable header
 * </pre>
 *
 * <p>Only API Standard header names are read (X-Internal-Api-Key,
 * X-Idempotency-Key, X-Request-Id); there are no aliases for older names.
 *
 * <p>The access log wraps authentication and rate limiting, so a 401 or a 429
 * produces an access line like any other request — traffic that never got
 * through is precisely the traffic worth seeing. Only the relative order
 * matters to {@code FilterRegistrationBean}.
 *
 * <p>Servlet filters, not Spring Security: the design in docs/09 requires running
 * before the dispatcher, which HandlerInterceptors do not.
 *
 * <p>Authentication is a shared API key per calling service (ADR-010, revised).
 * {@code ApiKeyAuthenticator} is the seam.
 *
 * <p>Bean naming convention: every method returns a {@link FilterRegistrationBean}
 * and is named {@code <filter>Registration}, which keeps these bean names out of
 * the namespace Spring Boot registers its own filters under — a bare
 * {@code requestContextFilter} clashes with {@code WebMvcAutoConfiguration} and
 * fails the context at startup.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(1);
        return registration;
    }

    /**
     * The access log. {@code @ConditionalOnProperty} rather than a branch inside
     * the filter: with {@code logging.request.enabled=false} the bean is never
     * created, so the filter is not in the chain at all and costs nothing.
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
            ApiKeyAuthenticator authenticator, ErrorResponseWriter errorWriter, MeterRegistry meters) {
        var registration = new FilterRegistrationBean<>(
                new InternalCallerFilter(authenticator, errorWriter, meters));
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            ApiKeyAuthenticator authenticator, ErrorResponseWriter errorWriter, MeterRegistry meters) {
        var registration = new FilterRegistrationBean<>(
                new TenantContextFilter(authenticator, errorWriter, meters));
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
