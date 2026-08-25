package com.aigreentick.services.storage.domain.media;

/** Malware-scan outcome. Column ships in Phase 2; the scanner arrives in Phase 4. */
public enum ScanStatus {
    SKIPPED,
    PENDING,
    CLEAN,
    INFECTED,
    FAILED
}
