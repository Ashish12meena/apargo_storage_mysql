package com.aigreentick.services.storage.domain.quota;

/**
 * Quota is enforced at two levels. Reservation always locks PROJECT first, then
 * ORG, and never the reverse — consistent ordering is what prevents deadlock
 * between concurrent uploads to different projects of one org.
 */
public enum QuotaScope {
    PROJECT,
    ORG
}
