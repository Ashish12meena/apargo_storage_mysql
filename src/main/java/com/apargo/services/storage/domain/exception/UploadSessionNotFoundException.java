package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * No such upload session for this tenant.
 */
public class UploadSessionNotFoundException extends DomainException {

    public UploadSessionNotFoundException(String internalMessage) {
        super(ErrorCode.UPLOAD_SESSION_NOT_FOUND, internalMessage);
    }
}
