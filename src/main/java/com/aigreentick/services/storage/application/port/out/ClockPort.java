package com.aigreentick.services.storage.application.port.out;

import java.time.Instant;

/** Injectable time source, so lifecycle and TTL logic is testable without sleeps. */
public interface ClockPort {

    Instant now();
}
