package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code media.scanning.*} — malware scanning, off by default.
 *
 * <p>The capability is always wired; only the scanner implementation changes. With
 * {@code enabled: false} uploads are marked {@code SKIPPED} and no scan event is
 * emitted, so enabling it later is a YAML flip plus a scanner adapter — no schema
 * change, no lifecycle change, no API change.
 *
 * <p>Scanning is NEVER in the request path. It runs off {@code media.created}
 * through the outbox, so a slow or unavailable scanner cannot fail an upload.
 *
 * @param blockDownloadUntilScanned when true, a file is unreadable until it comes
 *                                  back CLEAN. Stricter, but it makes upload
 *                                  latency visible to the reader, so it is off by
 *                                  default and is a per-deployment policy call.
 */
@Validated
@ConfigurationProperties(prefix = "media.scanning")
public record ScanningProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean blockDownloadUntilScanned,
        @DefaultValue("PT30S") Duration timeout,
        @DefaultValue("104857600") long maxScanBytes,
        /** Implementation selector: noop | clamav. */
        @DefaultValue("noop") String provider,
        String endpoint) {
}
