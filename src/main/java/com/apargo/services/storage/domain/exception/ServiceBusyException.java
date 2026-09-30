package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * A bounded resource (e.g. concurrent local-disk streams) is exhausted.
 * HTTP 503 {@code SERVICE_UNAVAILABLE} with {@code Retry-After}.
 */
public class ServiceBusyException extends DomainException {

    public ServiceBusyException(String internalMessage) {
        super(ErrorCode.SERVICE_UNAVAILABLE, internalMessage);
    }
}
