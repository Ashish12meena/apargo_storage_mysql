package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * The active provider cannot perform this operation — e.g. presigned PUT against
 * the local filesystem adapter. Callers fall back to the proxied path.
 */
public class UnsupportedStorageOperationException extends DomainException {

    public UnsupportedStorageOperationException(String internalMessage) {
        super(ErrorCode.OPERATION_UNSUPPORTED, internalMessage);
    }
}
