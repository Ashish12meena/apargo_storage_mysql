package com.aigreentick.services.storage.exception;

public class StorageLimitExceededException extends RuntimeException {
    public StorageLimitExceededException(String message) {
        super(message);
    }
    public StorageLimitExceededException(String message,Exception e) {
        super(message);
    }
    
}
