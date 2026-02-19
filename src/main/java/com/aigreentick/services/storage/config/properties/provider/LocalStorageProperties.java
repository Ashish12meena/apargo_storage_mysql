package com.aigreentick.services.storage.config.properties.provider;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage.providers.local")
public class LocalStorageProperties {
    private boolean enabled = true;
    private String rootPath = "./media-uploads";
    private String baseUrl = "http://localhost:7998/api/v1/media/";
}