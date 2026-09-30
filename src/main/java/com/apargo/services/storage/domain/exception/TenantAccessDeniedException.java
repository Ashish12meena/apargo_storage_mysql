package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * Required scope missing. Thrown before any lookup, so the caller learns nothing about existence.
 */
public class TenantAccessDeniedException extends DomainException {

    public TenantAccessDeniedException(String internalMessage) {
        super(ErrorCode.FORBIDDEN, internalMessage);
    }
}
