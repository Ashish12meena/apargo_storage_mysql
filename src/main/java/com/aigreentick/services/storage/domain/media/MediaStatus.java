package com.aigreentick.services.storage.domain.media;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle state of a media row. The transition table is enforced here, which is
 * why {@code Media} is not a JPA entity with public setters.
 */
public enum MediaStatus {

    PENDING,
    ACTIVE,
    EXPIRED,
    DELETED,
    PURGED,
    QUARANTINED;

    private static final Map<MediaStatus, Set<MediaStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(ACTIVE, EXPIRED),
            ACTIVE, EnumSet.of(DELETED, QUARANTINED),
            DELETED, EnumSet.of(ACTIVE, PURGED),
            QUARANTINED, EnumSet.of(DELETED),
            EXPIRED, EnumSet.noneOf(MediaStatus.class),
            PURGED, EnumSet.noneOf(MediaStatus.class));

    public boolean canTransitionTo(MediaStatus target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(MediaStatus.class)).contains(target);
    }

    public boolean isTerminal() {
        return this == PURGED || this == EXPIRED;
    }
}
