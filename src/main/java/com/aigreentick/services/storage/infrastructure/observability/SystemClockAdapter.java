package com.aigreentick.services.storage.infrastructure.observability;

import com.aigreentick.services.storage.application.port.out.ClockPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Injectable time source. Exists so TTL and lifecycle logic is testable by
 * substitution rather than by sleeping.
 */
@Component
public class SystemClockAdapter implements ClockPort {

    private final Clock clock;

    public SystemClockAdapter() {
        this(Clock.systemUTC());
    }

    public SystemClockAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
