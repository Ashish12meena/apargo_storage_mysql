package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * CORS policy, bound from {@code cors.*}.
 *
 * <p>Lifted out of {@code SecurityProperties} because CORS had grown from one
 * origin list into six related settings, and the methods, headers and max-age
 * were hardcoded in {@code WebConfig} where no operator could see them.
 *
 * <p><b>One knob.</b> Change {@code allowed-origins} and everything follows:
 *
 * <pre>
 *   allowed-origins: '*'                              → any origin
 *   allowed-origins: https://app.example.com          → that origin only
 *   allowed-origins: https://a.com, https://b.com     → both
 *   allowed-origins: https://*.example.com            → any subdomain
 *   allowed-origins: ''                               → CORS off entirely
 * </pre>
 *
 * <p>Credentials work in ALL of those, including {@code *}. That is the whole
 * reason {@code WebConfig} registers through {@code allowedOriginPatterns} rather
 * than {@code allowedOrigins}: the latter throws {@code IllegalArgumentException}
 * when {@code *} meets {@code allowCredentials}, and it throws from
 * {@code AbstractHandlerMapping#getHandler} — on every request, before any
 * controller — so the symptom is the entire API returning 400 with nothing
 * naming CORS. The pattern form echoes the request's actual {@code Origin} back
 * instead of a literal {@code *}, which is what the CORS specification requires,
 * so the pair is legal and no setting has to be silently overridden.
 *
 * @param allowedOrigins origins, or patterns, or {@code *}. Empty disables CORS.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @DefaultValue("*") List<String> allowedOrigins,
        @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) List<String> allowedMethods,
        @DefaultValue("*") List<String> allowedHeaders,
        @DefaultValue({"X-Trace-Id", "X-Request-Id", "X-RateLimit-Limit",
                "X-RateLimit-Remaining", "Retry-After"}) List<String> exposedHeaders,
        @DefaultValue("true") boolean allowCredentials,
        @DefaultValue("3600") long maxAgeSeconds) {

    public CorsProperties {
        allowedOrigins = clean(allowedOrigins);
        allowedMethods = clean(allowedMethods);
        allowedHeaders = clean(allowedHeaders);
        exposedHeaders = clean(exposedHeaders);
    }

    /** Trims and drops blanks, so a trailing comma in an env var is harmless. */
    private static List<String> clean(List<String> values) {
        return values == null ? List.of()
                : values.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** No origins configured means no CORS mapping is registered at all. */
    public boolean enabled() {
        return !allowedOrigins.isEmpty();
    }

    public String[] originsArray() {
        return allowedOrigins.toArray(String[]::new);
    }

    public String[] methodsArray() {
        return allowedMethods.toArray(String[]::new);
    }

    public String[] allowedHeadersArray() {
        return allowedHeaders.toArray(String[]::new);
    }

    public String[] exposedHeadersArray() {
        return exposedHeaders.toArray(String[]::new);
    }
}