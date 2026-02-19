package com.aigreentick.services.storage.enums;

public enum StorageProviderType {
    LOCAL("Local Filesystem"),
    S3("Amazon S3"),
    AZURE_BLOB("Azure Blob Storage"),
    GCS("Google Cloud Storage"),
    MINIO("MinIO S3-Compatible");
    
    private final String displayName;
    
    StorageProviderType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}