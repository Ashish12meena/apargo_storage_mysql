package com.aigreentick.services.storage.integration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;


@Data
@Configuration
@ConfigurationProperties(prefix = "organisation-service")   // fixed: was colliding with user-service
public class OrganisationClientProperties {
    private String baseUrl;
    private volatile boolean outgoingEnabled = true;
}