package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * A provisioning request would break the quota hierarchy — for example a project
 * limit above its organisation's total.
 *
 * <p>Distinct from {@link QuotaExceededException}: that means "this tenant is out
 * of space" (507). This means "the limit you are trying to set is not a legal
 * limit" (400), and the remedy is an administrative correction, not deleting files.
 */
public class InvalidQuotaLimitException extends DomainException {

    public InvalidQuotaLimitException(String internalMessage) {
        super(ErrorCode.QUOTA_LIMIT_INVALID, internalMessage);
    }
}
