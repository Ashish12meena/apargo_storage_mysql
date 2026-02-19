package com.aigreentick.services.storage.integration.facebook.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "whatsapp-service")   // keep yml key unchanged
public class FacebookClientProperties {
    private String baseUrl;
    private String apiVersion;
    private volatile boolean outgoingEnabled = true;
}
