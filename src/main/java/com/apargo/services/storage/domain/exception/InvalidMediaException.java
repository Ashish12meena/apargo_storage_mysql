package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * Size, filename, or structural validation failure.
 */
public class InvalidMediaException extends DomainException {

    public InvalidMediaException(String internalMessage) {
        super(ErrorCode.MEDIA_INVALID, internalMessage);
    }
}
