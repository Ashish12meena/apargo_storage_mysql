package com.aigreentick.services.storage.infrastructure.outbox;

import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.EventPublisherPort;
import com.aigreentick.services.storage.application.port.out.MalwareScannerPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.config.properties.ScanningProperties;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.ScanStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@code media.created} by scanning the stored object — asynchronously,
 * outside the request path.
 *
 * <p>With scanning disabled this is a no-op that acknowledges immediately, so the
 * event flow is identical whether or not a scanner is configured. That is the
 * point: turning scanning on later changes configuration, not code paths.
 */
@Component
@Slf4j
public class MediaScanHandler implements EventPublisherPort {

    private final MalwareScannerPort scanner;
    private final MediaRepositoryPort mediaRepository;
    private final ScanningProperties properties;
    private final ClockPort clock;
    private final MeterRegistry meters;

    public MediaScanHandler(MalwareScannerPort scanner, MediaRepositoryPort mediaRepository,
                            ScanningProperties properties, ClockPort clock, MeterRegistry meters) {
        this.scanner = scanner;
        this.mediaRepository = mediaRepository;
        this.properties = properties;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public String eventType() {
        return "media.created";
    }

    @Override
    public void handle(OutboxPort.OutboxRecord record) {
        if (!properties.enabled() || !scanner.isEnabled()) {
            return;
        }
        MediaId mediaId = MediaId.parse(record.aggregateId());
        Optional<Media> found = mediaRepository.findByIdForMaintenance(mediaId);
        if (found.isEmpty()) {
            return;                       // deleted before the scan ran; nothing to do
        }
        Media media = found.get();
        if (media.billableSize().value() > properties.maxScanBytes()) {
            log.info("media {} exceeds max-scan-bytes; marking SKIPPED", mediaId);
            media.recordScanResult(ScanStatus.SKIPPED, clock.now());
            mediaRepository.save(media);
            return;
        }

        ScanStatus result = scanner.scan(media.storageKey(), media.billableSize().value());

        // recordScanResult quarantines on INFECTED, so the state machine — not this
        // handler — decides what an infected verdict means.
        media.recordScanResult(result, clock.now());
        mediaRepository.save(media);

        meters.counter("storage.scan.completed", "result", result.name()).increment();
        if (result == ScanStatus.INFECTED) {
            log.error("media {} QUARANTINED: scanner returned INFECTED", mediaId);
        }
    }
}
