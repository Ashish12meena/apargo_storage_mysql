package com.aigreentick.services.storage.integration.organisation.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "organisation-service")   // fixed: was colliding with user-service
public class OrganisationClientProperties {
    private String baseUrl;
    private volatile boolean outgoingEnabled = true;
}