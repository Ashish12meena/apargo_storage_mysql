package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * Base for business-rule violations.
 *
 * <p>{@link #getMessage()} is for LOGS and may contain internal detail (storage
 * keys, paths, provider errors). {@link #clientMessage()} is what reaches the
 * caller and must contain none of it — the predecessor returned
 * {@code getMessage()} straight to clients and leaked storage keys in 404 bodies.
 */
public abstract class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, String internalMessage) {
        super(internalMessage);
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, String internalMessage, Throwable cause) {
        super(internalMessage, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String clientMessage() {
        return errorCode.defaultMessage();
    }
}
