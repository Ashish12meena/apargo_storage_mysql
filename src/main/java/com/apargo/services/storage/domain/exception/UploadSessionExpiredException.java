package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/** Commit arrived after the TTL lapsed and quota was already reclaimed. */
public class UploadSessionExpiredException extends DomainException {

    public UploadSessionExpiredException(String internalMessage) {
        super(ErrorCode.UPLOAD_SESSION_EXPIRED, internalMessage);
    }
}
