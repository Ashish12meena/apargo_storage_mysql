package com.aigreentick.services.storage.domain.exception;

import com.aigreentick.services.storage.common.error.ErrorCode;

/**
 * The batch itself is malformed — no files at all.
 *
 * <p>A request-level rejection, NOT a batch result: returning success with zero
 * results would let a caller whose multipart field name is wrong believe every
 * file was processed.
 */
public class InvalidBatchException extends DomainException {

    public InvalidBatchException(String internalMessage) {
        super(ErrorCode.BATCH_FILES_REQUIRED, internalMessage);
    }
}
