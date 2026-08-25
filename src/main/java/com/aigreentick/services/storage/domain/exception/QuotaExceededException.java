package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.domain.quota.QuotaScope;

/** Maps to 507, preserved from the predecessor for downstream compatibility. */
public class QuotaExceededException extends DomainException {

    private final transient QuotaScope scope;
    private final long requestedBytes;
    private final long availableBytes;

    public QuotaExceededException(QuotaScope scope, long requestedBytes, long availableBytes) {
        super(ErrorCode.QUOTA_EXCEEDED,
                "quota exceeded at scope=" + scope + " requested=" + requestedBytes
                        + " available=" + availableBytes);
        this.scope = scope;
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
    }

    public QuotaScope scope() { return scope; }
    public long requestedBytes() { return requestedBytes; }
    public long availableBytes() { return availableBytes; }
}
