package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * The key was previously used for a request with a different payload.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String internalMessage) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, internalMessage);
    }
}
