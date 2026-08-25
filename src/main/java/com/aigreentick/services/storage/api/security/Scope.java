package com.aigreentick.services.storage.api.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Permissions carried as JWT claims. Scope grants the ABILITY; the tenant claim
 * bounds the REACH. Both are required — {@code media:delete} does not permit
 * deleting another tenant's file, because every lookup is tenant-scoped
 * independently of scope.
 */
public enum Scope {

    MEDIA_READ("media:read"),
    MEDIA_WRITE("media:write"),
    MEDIA_DELETE("media:delete"),
    /** Skips the recovery grace period. Compliance erasure only. */
    MEDIA_DELETE_PERMANENT("media:delete:permanent"),
    QUOTA_READ("quota:read"),
    /** Mutates quota LIMITS. Service credentials only, never end users. */
    QUOTA_ADMIN("quota:admin"),

    /**
     * Wipes every file for a project or an organisation.
     *
     * <p>Deliberately NOT implied by {@code media:delete}. Deleting a file and
     * deleting a customer's entire library are different blast radii, and the
     * second should require a credential the first does not have.
     */
    TENANT_TEARDOWN("tenant:teardown");

    private final String value;

    Scope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<Scope> fromValue(String raw) {
        return Arrays.stream(values()).filter(s -> s.value.equals(raw)).findFirst();
    }
}
