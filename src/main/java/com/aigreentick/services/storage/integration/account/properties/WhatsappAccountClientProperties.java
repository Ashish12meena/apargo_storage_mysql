package com.aigreentick.services.storage.integration.account.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "waba-service")   
public class WhatsappAccountClientProperties {
    private String baseUrl;
    private volatile boolean outgoingEnabled = true;
}