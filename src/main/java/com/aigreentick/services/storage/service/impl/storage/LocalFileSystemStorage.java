package com.aigreentick.services.storage.service.impl.storage;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.aigreentick.services.storage.config.properties.provider.LocalStorageProperties;
import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.StorageProviderType;
import com.aigreentick.services.storage.exception.StorageException;
import com.aigreentick.services.storage.service.port.StoragePort;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage.providers.local", name = "enabled", havingValue = "true")
public class LocalFileSystemStorage implements StoragePort {
    
    private final LocalStorageProperties properties;
    
    @Override
    public StorageResult save(InputStream inputStream, StorageMetadata metadata) throws StorageException {
        try {
            String storageKey = metadata.generateStorageKey();
            Path filePath = resolveFilePath(storageKey);
            
            // Create directories if they don't exist
            Files.createDirectories(filePath.getParent());
            
            // Copy file to storage
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("File saved to local storage: {}", storageKey);
            
            return StorageResult.builder()
                    .storageKey(storageKey)
                    .publicUrl(properties.getBaseUrl() + storageKey)
                    .provider(StorageProviderType.LOCAL)
                    .bucket(properties.getRootPath())
                    .region("local")
                    .fileSize(metadata.getFileSize())
                    .contentType(metadata.getContentType())
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to save file to local storage", e);
            throw new StorageException("Failed to save file to local storage", e, StorageProviderType.LOCAL);
        }
    }
    
    @Override
    public InputStream retrieve(String storageKey) throws StorageException {
        try {
            Path filePath = resolveFilePath(storageKey);
            
            if (!Files.exists(filePath)) {
                throw new StorageException("File not found: " + storageKey, StorageProviderType.LOCAL, storageKey);
            }
            
            return new FileInputStream(filePath.toFile());
            
        } catch (IOException e) {
            log.error("Failed to retrieve file from local storage: {}", storageKey, e);
            throw new StorageException("Failed to retrieve file", e, StorageProviderType.LOCAL, storageKey);
        }
    }
    
    @Override
    public boolean delete(String storageKey) throws StorageException {
        try {
            Path filePath = resolveFilePath(storageKey);
            
            if (!Files.exists(filePath)) {
                log.warn("File not found for deletion: {}", storageKey);
                return false;
            }
            
            Files.delete(filePath);
            log.info("File deleted from local storage: {}", storageKey);
            return true;
            
        } catch (IOException e) {
            log.error("Failed to delete file from local storage: {}", storageKey, e);
            throw new StorageException("Failed to delete file", e, StorageProviderType.LOCAL, storageKey);
        }
    }
    
    @Override
    public boolean exists(String storageKey) {
        Path filePath = resolveFilePath(storageKey);
        return Files.exists(filePath);
    }
    
    @Override
    public String getPublicUrl(String storageKey, Duration expiry) {
        // Local storage doesn't support expiring URLs
        return properties.getBaseUrl() + storageKey;
    }
    
    @Override
    public StorageProviderType getProviderType() {
        return StorageProviderType.LOCAL;
    }
    
    private Path resolveFilePath(String storageKey) {
        return Paths.get(properties.getRootPath(), storageKey).normalize();
    }
}