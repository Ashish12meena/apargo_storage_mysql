package com.aigreentick.services.storage.domain.upload;

/**
 * How the bytes reach the storage backend. Both modes share one reservation and
 * commit protocol, so quota, validation, and lifecycle behave identically; the
 * mode only decides who moves the bytes.
 */
public enum UploadMode {

    /** Client → this service → storage. Small files only. */
    PROXIED,

    /** Client → storage, directly, via a presigned PUT. */
    PRESIGNED_SINGLE,

    /** Client → storage via presigned per-part URLs. */
    PRESIGNED_MULTIPART;

    public boolean isDirect() {
        return this != PROXIED;
    }
}
