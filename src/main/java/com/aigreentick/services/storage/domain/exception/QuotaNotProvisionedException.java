package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * No quota row exists. Distinct from exceeded — the remedy is admin provisioning, not deleting files.
 */
public class QuotaNotProvisionedException extends DomainException {

    public QuotaNotProvisionedException(String internalMessage) {
        super(ErrorCode.QUOTA_NOT_PROVISIONED, internalMessage);
    }
}
