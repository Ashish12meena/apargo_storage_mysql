package com.apargo.services.storage.domain.exception;

import com.apargo.services.storage.common.error.ErrorCode;

/**
 * No quota row exists. Distinct from exceeded — the remedy is admin provisioning, not deleting files.
 */
public class QuotaNotProvisionedException extends DomainException {

    public QuotaNotProvisionedException(String internalMessage) {
        super(ErrorCode.QUOTA_NOT_PROVISIONED, internalMessage);
    }
}
