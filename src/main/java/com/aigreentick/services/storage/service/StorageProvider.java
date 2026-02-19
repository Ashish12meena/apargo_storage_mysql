package com.aigreentick.services.storage.service;


import java.io.InputStream;
import java.time.Duration;

import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.StorageProviderType;
import com.aigreentick.services.storage.exception.StorageException;

public interface StorageProvider {
    
    /**
     * Save file to storage
     * @param inputStream file content
     * @param metadata file metadata
     * @return storage result with location details
     * @throws StorageException if save fails
     */
    StorageResult save(InputStream inputStream, StorageMetadata metadata) throws StorageException;
    
    /**
     * Retrieve file from storage
     * @param storageKey unique key identifying the file
     * @return input stream of file content
     * @throws StorageException if retrieval fails
     */
    InputStream retrieve(String storageKey) throws StorageException;
    
    /**
     * Delete file from storage
     * @param storageKey unique key identifying the file
     * @return true if deleted successfully
     * @throws StorageException if deletion fails
     */
    boolean delete(String storageKey) throws StorageException;
    
    /**
     * Check if file exists
     * @param storageKey unique key identifying the file
     * @return true if file exists
     */
    boolean exists(String storageKey);
    
    /**
     * Get public URL for file access
     * @param storageKey unique key identifying the file
     * @param expiry URL expiry duration (null for permanent)
     * @return publicly accessible URL
     */
    String getPublicUrl(String storageKey, Duration expiry);
    
    /**
     * Get storage provider type
     * @return provider type enum
     */
    StorageProviderType getProviderType();
}