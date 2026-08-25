package com.aigreentick.services.storage.domain.upload;

/**
 * Quota is charged at {@code RESERVED} and released on {@code ABORTED} or
 * {@code EXPIRED}. Charging at reservation rather than at commit is what makes
 * concurrent uploads correct: otherwise N concurrent uploads could all pass a
 * capacity check and collectively overcommit.
 */
public enum UploadSessionStatus {

    RESERVED,
    COMMITTED,
    ABORTED,
    EXPIRED;

    public boolean isTerminal() {
        return this != RESERVED;
    }

    /** Only a RESERVED session still holds quota. */
    public boolean holdsQuota() {
        return this == RESERVED;
    }
}
