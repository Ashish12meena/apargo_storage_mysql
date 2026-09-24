package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * A create operation arrived without a usable {@code X-Idempotency-Key}
 * (API Standard §1: required on create/send APIs). HTTP 400.
 */
public class IdempotencyKeyRequiredException extends DomainException {

    public IdempotencyKeyRequiredException(String internalMessage) {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, internalMessage);
    }
}
