package com.aigreentick.services.storage.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "media.service")
public class MediaProperties {
    private volatile boolean incomingEnabled = true;

    private String uploadPath; // Corresponds to media.service.upload.path
    private String baseUrl;

    // Properties for media upload constraints
    private long uploadMaxSize = 52428800; // Default: 50MB
    private List<String> uploadAllowedImageTypes;
    private List<String> uploadAllowedVideoTypes;
    private List<String> uploadAllowedDocumentTypes;
    private List<String> uploadAllowedAudioTypes;

     /**
     * Check if a MIME type is supported by WhatsApp
     */
    public boolean isSupported(String mime) {
        return uploadAllowedImageTypes.contains(mime) 
            || uploadAllowedVideoTypes.contains(mime) 
            || uploadAllowedAudioTypes.contains(mime) 
            || uploadAllowedDocumentTypes.contains(mime);
    }
}
