package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.config.properties.CorsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties cors;

    public WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    /** The effective policy on the log, so it is never inferred from a browser console. */
    @PostConstruct
    void logCorsPolicy() {
        if (!cors.enabled()) {
            log.info("CORS disabled: cors.allowed-origins is empty. "
                    + "Browser requests from any origin will be blocked.");
            return;
        }
        log.info("CORS enabled on /api/**: origins={} methods={} credentials={} maxAge={}s",
                cors.allowedOrigins(), cors.allowedMethods(),
                cors.allowCredentials(), cors.maxAgeSeconds());
    }

    /**
     * CORS for the tenant-facing API.
     *
     * <p>Registered with {@code allowedOriginPatterns}, never {@code allowedOrigins},
     * and that is the single decision that makes this configuration behave the way
     * an operator expects at every setting.
     *
     * <p>{@code allowedOrigins("*")} combined with credentials throws
     * {@code IllegalArgumentException} from {@code AbstractHandlerMapping#getHandler}.
     * That runs on EVERY request before any controller, so the failure presents as
     * the whole API returning {@code 400 REQUEST_INVALID} — including endpoints
     * that take no parameters at all — with nothing in the response mentioning
     * CORS. It is a genuinely hard failure to trace back to a config line.
     *
     * <p>{@code allowedOriginPatterns} echoes the request's actual {@code Origin}
     * header back rather than a literal {@code *}, which is exactly what the CORS
     * specification requires alongside credentials. So one code path serves every
     * case — {@code *}, one origin, several origins, or a subdomain pattern — and
     * credentials stay honoured in all of them. Nothing is silently overridden,
     * because nothing needs to be.
     *
     * <p>Mapped to {@code /api/**} only. {@code /internal/**} is excluded
     * deliberately: it is reached by services, never by a browser, and a preflight
     * succeeding there would be a signal that something is calling it from a page.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!cors.enabled()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(cors.originsArray())
                .allowedMethods(cors.methodsArray())
                .allowedHeaders(cors.allowedHeadersArray())
                .exposedHeaders(cors.exposedHeadersArray())
                .allowCredentials(cors.allowCredentials())
                .maxAge(cors.maxAgeSeconds());
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}