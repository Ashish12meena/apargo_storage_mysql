package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * An attempted lifecycle transition the state machine forbids.
 */
public class IllegalMediaStateException extends DomainException {

    public IllegalMediaStateException(String internalMessage) {
        super(ErrorCode.MEDIA_ILLEGAL_STATE, internalMessage);
    }
}
