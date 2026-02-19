package com.aigreentick.services.storage.exception;

import com.aigreentick.services.storage.enums.StorageProviderType;

public class StorageException extends RuntimeException {
    private final StorageProviderType providerType;
    private final String storageKey;

    public StorageException(String message, StorageProviderType providerType) {
        super(message);
        this.providerType = providerType;
        this.storageKey = null;
    }
    public StorageException(String message, Throwable cause, StorageProviderType providerType) {
        super(message, cause);
        this.providerType = providerType;
        this.storageKey = null;
    }
    public StorageException(String message, Throwable cause, StorageProviderType providerType, String storageKey) {
        super(message, cause);
        this.providerType = providerType;
        this.storageKey = storageKey;
    }
    public StorageException(String message, StorageProviderType providerType, String storageKey) {
        super(message);
        this.providerType = providerType;
        this.storageKey = storageKey;
    }

    public StorageProviderType getProviderType() { return providerType; }
    public String getStorageKey() { return storageKey; }
}