package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * Exceeds the configured ceiling for its media type or the absolute maximum.
 */
public class MediaTooLargeException extends DomainException {

    public MediaTooLargeException(String internalMessage) {
        super(ErrorCode.MEDIA_TOO_LARGE, internalMessage);
    }
}
