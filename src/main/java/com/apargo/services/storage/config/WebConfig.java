package com.apargo.services.storage.config;

import com.apargo.services.storage.api.security.IdempotencyKeyInterceptor;
import com.apargo.services.storage.common.constants.ApiPaths;
import com.apargo.services.storage.config.properties.CorsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties cors;
    private final IdempotencyKeyInterceptor idempotencyKeyInterceptor;

    public WebConfig(CorsProperties cors, IdempotencyKeyInterceptor idempotencyKeyInterceptor) {
        this.cors = cors;
        this.idempotencyKeyInterceptor = idempotencyKeyInterceptor;
    }

    /** Enforces X-Idempotency-Key on {@code @RequiresIdempotencyKey} handlers only. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyKeyInterceptor).addPathPatterns(ApiPaths.API_V1 + "/**");
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

    // CORS itself is applied by the CorsFilter registered in SecurityConfig, ahead
    // of authentication; an MVC mapping here would run too late for preflights.

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}