package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * Detected type is not on the allowlist.
 */
public class ContentTypeNotAllowedException extends DomainException {

    public ContentTypeNotAllowedException(String internalMessage) {
        super(ErrorCode.CONTENT_TYPE_NOT_ALLOWED, internalMessage);
    }
}
