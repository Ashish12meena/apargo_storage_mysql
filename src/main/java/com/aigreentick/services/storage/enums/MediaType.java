package com.aigreentick.services.storage.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MediaType {
    IMAGE("IMAGE", 5L * 1024 * 1024), // 5 MB
    VIDEO("VIDEO", 16L * 1024 * 1024), // 16 MB
    DOCUMENT("DOCUMENT", 100L * 1024 * 1024), // 100 MB
    AUDIO("AUDIO", 16L * 1024 * 1024),
    PRODUCT("PRODUCT",0);

    private final String value;
    private final long maxBytes;

    MediaType(String value, long maxBytes) {
        this.value = value;
        this.maxBytes = maxBytes;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    @JsonCreator
    public static MediaType fromValue(String input) {
        if (input == null) {
            return null;
        }
        for (MediaType category : MediaType.values()) {
            if (category.value.equalsIgnoreCase(input)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + input);
    }

    public static class constants {
        public static final String IMAGE = "IMAGE";
        public static final String DOCUMENT = "DOCUMENT";
        public static final String VIDEO = "VIDEO";
        public static final String AUDIO = "AUDIO";
        public static final Long ABSOLUTE_MAX = 100L * 1024 * 1024;
    }
}
