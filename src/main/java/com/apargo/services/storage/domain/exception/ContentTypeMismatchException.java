package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * Declared MIME type disagrees with the bytes. Always a rejection, never a silent correction.
 */
public class ContentTypeMismatchException extends DomainException {

    public ContentTypeMismatchException(String internalMessage) {
        super(ErrorCode.CONTENT_TYPE_MISMATCH, internalMessage);
    }
}
