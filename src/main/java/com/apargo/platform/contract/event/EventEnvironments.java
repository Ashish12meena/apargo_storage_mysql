package com.apargo.platform.contract.event;

import java.util.Set;

/** Allowed values of the {@code environment} field. */
public final class EventEnvironments {

    public static final String DEVELOPMENT = "development";
    public static final String STAGING = "staging";
    public static final String PRODUCTION = "production";

    public static final Set<String> ALL = Set.of(DEVELOPMENT, STAGING, PRODUCTION);

    private EventEnvironments() {
    }

    public static boolean isValid(String environment) {
        return environment != null && ALL.contains(environment);
    }
}
