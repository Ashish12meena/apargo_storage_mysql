package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * Returned for a cross-tenant access attempt as well as a genuine miss: a 403 would confirm existence and make the endpoint an enumeration oracle.
 */
public class MediaNotFoundException extends DomainException {

    public MediaNotFoundException(String internalMessage) {
        super(ErrorCode.MEDIA_NOT_FOUND, internalMessage);
    }
}
