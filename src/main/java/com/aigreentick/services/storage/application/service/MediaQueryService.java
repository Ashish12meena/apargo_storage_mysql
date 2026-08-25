package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.QueryMediaUseCase;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.application.shared.MediaListQuery;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.application.shared.PageView;
import com.aigreentick.services.storage.config.properties.ScanningProperties;
import com.aigreentick.services.storage.domain.exception.MediaNotFoundException;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.ScanStatus;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Read side.
 *
 * <p>Absorbs the read methods that lived on the predecessor's unwired upload
 * orchestrator — the class was half-live, so deleting it wholesale would have
 * broken every read path (docs/17, R-7).
 */
@Service
public class MediaQueryService implements QueryMediaUseCase {

    private final MediaRepositoryPort mediaRepository;
    private final StoragePort storage;
    private final MediaViewMapper viewMapper;
    private final ScanningProperties scanningProperties;

    public MediaQueryService(MediaRepositoryPort mediaRepository, StoragePort storage,
                             MediaViewMapper viewMapper, ScanningProperties scanningProperties) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.viewMapper = viewMapper;
        this.scanningProperties = scanningProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public MediaView getById(MediaId id, TenantRef tenant) {
        return viewMapper.toView(requireReadable(id, tenant));
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<MediaView> list(MediaListQuery query) {
        // No presigned URL per row: minting one per item on a 100-item page is
        // measurable work for a value most list callers never use.
        return mediaRepository.search(query).map(viewMapper::toViewWithoutUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public String generateDownloadUrl(MediaId id, TenantRef tenant, Duration ttl) {
        Media media = requireReadable(id, tenant);
        // Defence in depth: the key was resolved server-side from a tenant-scoped
        // lookup, so this can only fail if data is corrupt — but it is the check
        // that makes a leaked key useless, so it runs on every read path.
        if (!media.storageKey().belongsTo(tenant)) {
            throw new MediaNotFoundException("storage key outside tenant prefix for media " + id);
        }
        return storage.presignGet(media.storageKey(), ttl);
    }

    private Media requireReadable(MediaId id, TenantRef tenant) {
        Media media = mediaRepository.findByIdForTenant(id, tenant)
                .orElseThrow(() -> new MediaNotFoundException("media " + id + " not found for " + tenant));
        if (!media.isReadable()) {
            // A deleted or quarantined item is indistinguishable from absent.
            throw new MediaNotFoundException("media " + id + " is " + media.status());
        }
        // Optional stricter policy: withhold a file until a scan verdict exists.
        // Off by default, because it makes scan latency visible to every reader.
        if (scanningProperties.enabled() && scanningProperties.blockDownloadUntilScanned()
                && media.scanStatus() == ScanStatus.PENDING) {
            throw new MediaNotFoundException("media " + id + " is awaiting a scan verdict");
        }
        return media;
    }
}
