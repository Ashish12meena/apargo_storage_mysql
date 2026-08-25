package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.config.properties.CorsProperties;
import com.aigreentick.services.storage.config.properties.MediaValidationProperties;
import com.aigreentick.services.storage.config.properties.OutboxProperties;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.config.properties.RateLimitProperties;
import com.aigreentick.services.storage.config.properties.RequestLoggingProperties;
import com.aigreentick.services.storage.config.properties.ScanningProperties;
import com.aigreentick.services.storage.config.properties.SecurityProperties;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the typed configuration records.
 *
 * <p>Needed because {@code @ComponentScan} does NOT pick these up:
 * {@code @ConfigurationProperties} is a binding marker, not a stereotype, so the
 * scan walks straight past a plain record that carries no {@code @Component}.
 *
 * <p>Explicit registration rather than {@code @ConfigurationPropertiesScan}:
 * forgetting to add the next record here fails at startup with a
 * {@code NoSuchBeanDefinitionException} naming the missing class. Loud and
 * immediate. A scan would instead silently skip a record placed outside the
 * scanned package, which is a far more confusing failure.
 *
 * <p>Once the ArchUnit rule requiring every {@code @ConfigurationProperties} class
 * to live in {@code config.properties} is in place (Phase 1), this can become a
 * bare {@code @ConfigurationPropertiesScan} — the rule makes the silent-skip case
 * unreachable, and the list stops needing maintenance.
 */
@Configuration
@EnableConfigurationProperties({
        StorageProperties.class,
        MediaValidationProperties.class,
        ScanningProperties.class,
        QuotaProperties.class,
        SecurityProperties.class,
        CorsProperties.class,
        RateLimitProperties.class,
        OutboxProperties.class,
        RequestLoggingProperties.class
})
public class PropertiesConfig {
}