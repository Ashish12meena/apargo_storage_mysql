package com.apargo.platform.contract.event;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Event identifiers: UUIDv7 (RFC 9562) — time-ordered, so ids sort roughly by
 * creation time and index well, and random enough to be globally unique.
 *
 * <p>An event id is generated ONCE, when the event is built, and reused for
 * every delivery attempt; consumers de-duplicate on it.
 */
public final class EventIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private EventIds() {
    }

    /** A new UUIDv7 in canonical lower-case form. */
    public static String newId() {
        return newUuidV7(System.currentTimeMillis()).toString();
    }

    /** True when the value is a canonical lower-case UUID of version 7. */
    public static boolean isValid(String id) {
        if (id == null || !UUID_PATTERN.matcher(id).matches()) {
            return false;
        }
        UUID uuid = UUID.fromString(id);
        return uuid.version() == 7 && uuid.variant() == 2;
    }

    static UUID newUuidV7(long epochMillis) {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        // 48-bit big-endian Unix timestamp in milliseconds.
        long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16;
        // Version 7 in the high nibble of the next 16 bits, then 12 random bits.
        msb |= 0x7000L | (((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL));

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (random[i] & 0xFFL);
        }
        // IETF variant (binary 10) in the two most significant bits.
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}
