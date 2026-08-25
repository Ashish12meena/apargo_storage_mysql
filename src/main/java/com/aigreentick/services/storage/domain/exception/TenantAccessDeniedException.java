package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * Required scope missing. Thrown before any lookup, so the caller learns nothing about existence.
 */
public class TenantAccessDeniedException extends DomainException {

    public TenantAccessDeniedException(String internalMessage) {
        super(ErrorCode.ACCESS_DENIED, internalMessage);
    }
}
