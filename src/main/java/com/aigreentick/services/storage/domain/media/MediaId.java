package com.aigreentick.services.storage.domain.media;

/**
 * Public identifier of a media object.
 *
 * <p>OPEN DECISION OD-2: this is the auto-increment surrogate key, which is
 * enumerable and leaks volume. Tenant-scoped lookups mean enumeration yields 404s
 * rather than data, so the residual risk is volume disclosure only.
 */
public record MediaId(long value) {

    public MediaId {
        if (value <= 0) {
            throw new IllegalArgumentException("mediaId must be positive");
        }
    }

    public static MediaId of(long value) {
        return new MediaId(value);
    }

    public static MediaId parse(String raw) {
        try {
            return new MediaId(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("mediaId is not a number");
        }
    }

    public String asString() {
        return Long.toString(value);
    }
}
