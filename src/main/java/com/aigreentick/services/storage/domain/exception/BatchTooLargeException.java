package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * More files than {@code storage.max-files-per-batch} permits.
 *
 * <p>Counted before any file is processed, so an oversized batch costs nothing.
 */
public class BatchTooLargeException extends DomainException {

    public BatchTooLargeException(int submitted, int maximum) {
        super(ErrorCode.BATCH_TOO_MANY_FILES,
                "batch of " + submitted + " files exceeds the maximum of " + maximum);
    }
}
