package com.apargo.services.storage.config;

import com.apargo.services.storage.api.error.ErrorResponseWriter;
import com.apargo.services.storage.api.security.ApiKeyAuthenticator;
import com.apargo.services.storage.api.security.InternalCallerFilter;
import com.apargo.services.storage.api.security.TenantContextFilter;
import com.apargo.services.storage.config.properties.CorsProperties;
import com.apargo.services.storage.config.properties.RateLimitProperties;
import com.apargo.services.storage.config.properties.RequestLoggingProperties;
import com.apargo.services.storage.infrastructure.observability.RequestLoggingFilter;
import com.apargo.services.storage.infrastructure.observability.RequestIdFilter;
import com.apargo.services.storage.infrastructure.observability.TraceContextFilter;
import com.apargo.services.storage.infrastructure.ratelimit.RateLimitFilter;
import com.apargo.services.storage.application.port.out.RateLimiterPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Filter chain, in order:
 *
 * <pre>
 *  -1 CorsFilter             answers browser preflights on /api/**; headers on every /api/** response
 *   0 TraceContextFilter     backend trace id (traceparent continued on /internal/** only)
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

    /**
     * CORS for the tenant-facing API, as the first filter in the chain.
     *
     * <p>It must run before {@code TenantContextFilter}. A browser preflight
     * carries none of the tenant or API-key headers, so when CORS lived in
     * {@code WebMvcConfigurer#addCorsMappings} the preflight was rejected with a
     * 400/401 before the dispatcher ever saw it, and that rejection had no
     * {@code Access-Control-*} headers: the browser reported "Failed to fetch".
     * Here the preflight is answered before authentication, and the 400 / 401 /
     * 413 / 429 written by later filters carry CORS headers too, so the page
     * sees the real error. Requests without an {@code Origin} header (every
     * service-to-service call) pass through untouched.
     *
     * <p>Registered with {@code allowedOriginPatterns}, never {@code allowedOrigins}:
     * the pattern form echoes the request's actual {@code Origin} back rather
     * than a literal {@code *}, which is what the CORS specification requires
     * alongside credentials. So one code path serves every case ({@code *}, one
     * origin, several, or a subdomain pattern) with credentials honoured in all.
     *
     * <p>Mapped to {@code /api/**} only. {@code /internal/**} is excluded
     * deliberately: it is reached by services, never by a browser, and a preflight
     * succeeding there would be a signal that something is calling it from a page.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsProperties cors) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (cors.enabled()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOriginPatterns(cors.allowedOrigins());
            config.setAllowedMethods(cors.allowedMethods());
            config.setAllowedHeaders(cors.allowedHeaders());
            config.setExposedHeaders(cors.exposedHeaders());
            config.setAllowCredentials(cors.allowCredentials());
            config.setMaxAge(cors.maxAgeSeconds());
            source.registerCorsConfiguration("/api/**", config);
        }
        var registration = new FilterRegistrationBean<>(new CorsFilter(source));
        // CorsFilter ends every preflight it sees, configured path or not, so it
        // is mounted on /api/* alone: everything else reaches the chain as before.
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TraceContextFilter> traceContextFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new TraceContextFilter());
        registration.setOrder(0);
        return registration;
    }

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
