package com.apargo.platform.contract.event;

/**
 * Version of the event JSON schema. Adding optional fields keeps the version;
 * removing or renaming a field, or changing its meaning, requires a new one.
 */
public final class EventSchemaVersion {

    /** Version written by this contract. */
    public static final int CURRENT = 1;

    private EventSchemaVersion() {
    }
}
