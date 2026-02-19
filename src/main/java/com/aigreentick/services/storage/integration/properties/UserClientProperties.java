package com.aigreentick.services.storage.integration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "user-service")
@Data
public class UserClientProperties {
    private String baseUrl;
    private String apiVersion;

     private volatile boolean outgoingEnabled = true;
}

