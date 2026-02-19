package com.aigreentick.services.storage.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageProperties {
    private String root;
    private String tempDir;
    private long maxFileSize = 524288000L; // 500MB
    private List<String> allowedMimeTypes;
}
