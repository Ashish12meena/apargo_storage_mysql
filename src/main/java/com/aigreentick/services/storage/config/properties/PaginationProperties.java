package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for pagination settings.
 * Maps to application.yml under 'pagination' prefix.
 */
@Data
@Component
@ConfigurationProperties(prefix = "pagination")
public class PaginationProperties {
    /**
     * Maximum allowed page size to prevent resource exhaustion.
     */
    private Integer maxPageSize = 100;
    
    /**
     * Default page size when not specified in request.
     */
    private Integer defaultPageSize = 20;
    
    /**
     * Minimum page size allowed.
     */
    private Integer minPageSize = 1;
}
