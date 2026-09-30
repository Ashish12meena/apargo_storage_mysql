package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * An identical request is currently being processed.
 */
public class RequestInProgressException extends DomainException {

    public RequestInProgressException(String internalMessage) {
        super(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS, internalMessage);
    }
}
