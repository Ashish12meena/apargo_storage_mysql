package com.aigreentick.services.storage.dto.storage;

import com.aigreentick.services.storage.enums.StorageProviderType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageResult {
    private String storageKey;           // Provider-specific key/path
    private String publicUrl;            // Publicly accessible URL
    private StorageProviderType provider; // Which provider was used
    private String bucket;               // Bucket/container name
    private String region;               // Geographic region
    private Long fileSize;               // File size in bytes
    private String contentType;          // MIME type
}