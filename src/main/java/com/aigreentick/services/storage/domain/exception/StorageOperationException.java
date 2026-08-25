package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * The storage backend failed. Carries the provider for logs; the client sees only
 * the generic message — bucket names and provider detail never leave the service.
 */
public class StorageOperationException extends DomainException {

    private final transient String provider;
    private final transient String operation;

    public StorageOperationException(String provider, String operation, String internalMessage, Throwable cause) {
        super(ErrorCode.STORAGE_UNAVAILABLE,
                "storage " + operation + " failed on " + provider + ": " + internalMessage, cause);
        this.provider = provider;
        this.operation = operation;
    }

    public String provider() { return provider; }
    public String operation() { return operation; }
}
