package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * An identical request is currently being processed.
 */
public class RequestInProgressException extends DomainException {

    public RequestInProgressException(String internalMessage) {
        super(ErrorCode.REQUEST_IN_PROGRESS, internalMessage);
    }
}
