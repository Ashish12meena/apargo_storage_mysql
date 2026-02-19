package com.aigreentick.services.storage.service.port;


import com.aigreentick.services.storage.dto.storage.StorageMetadata;
import com.aigreentick.services.storage.dto.storage.StorageResult;
import com.aigreentick.services.storage.enums.StorageProviderType;
import com.aigreentick.services.storage.exception.StorageException;

import java.io.InputStream;
import java.time.Duration;

/**
 * Port (interface) for pluggable storage backends.
 * Implementations: LocalFileSystemStorage, S3StorageAdapter.
 */
public interface StoragePort {
    StorageResult save(InputStream inputStream, StorageMetadata metadata) throws StorageException;
    InputStream retrieve(String storageKey) throws StorageException;
    boolean delete(String storageKey) throws StorageException;
    boolean exists(String storageKey);
    String getPublicUrl(String storageKey, Duration expiry);
    StorageProviderType getProviderType();
}