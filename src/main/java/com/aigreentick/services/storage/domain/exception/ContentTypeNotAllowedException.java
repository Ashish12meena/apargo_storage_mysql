package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * Detected type is not on the allowlist.
 */
public class ContentTypeNotAllowedException extends DomainException {

    public ContentTypeNotAllowedException(String internalMessage) {
        super(ErrorCode.CONTENT_TYPE_NOT_ALLOWED, internalMessage);
    }
}
