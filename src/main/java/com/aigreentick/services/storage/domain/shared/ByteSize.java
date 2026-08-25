package com.aigreentick.services.storage.domain.shared;

/**
 * A non-negative quantity of bytes.
 *
 * <p>Underflow is rejected rather than clamped: releasing more than was reserved
 * is a bug to surface, and clamping is how drift hides.
 */
public record ByteSize(long value) implements Comparable<ByteSize> {

    public static final ByteSize ZERO = new ByteSize(0);

    public ByteSize {
        if (value < 0) {
            throw new IllegalArgumentException("byte size must not be negative: " + value);
        }
    }

    public static ByteSize of(long value) {
        return new ByteSize(value);
    }

    public ByteSize plus(ByteSize other) {
        return new ByteSize(Math.addExact(value, other.value));
    }

    public ByteSize minus(ByteSize other) {
        return new ByteSize(Math.subtractExact(value, other.value));
    }

    public boolean isZero() {
        return value == 0;
    }

    public boolean exceeds(ByteSize other) {
        return value > other.value;
    }

    @Override
    public int compareTo(ByteSize o) {
        return Long.compare(value, o.value);
    }
}
