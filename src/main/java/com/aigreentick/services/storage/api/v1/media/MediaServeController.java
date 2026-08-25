package com.aigreentick.services.storage.api.v1.media;

import com.aigreentick.services.storage.api.security.MediaAccessGuard;
import com.aigreentick.services.storage.api.security.Scope;
import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.api.security.TenantPrincipal;
import com.aigreentick.services.storage.application.port.out.AuditPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.domain.exception.MediaNotFoundException;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.StorageKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code /api/v1/media/serve/**} — streams objects from the LOCAL provider.
 *
 * <p>FROZEN ROUTE. Absolute URLs built from this path are persisted in another
 * service's database, so the path may never move. Behaviour may improve.
 *
 * <p>Everything below was missing in the predecessor and each absence was a real
 * defect: no ownership check at all (the route was explicitly excluded from the
 * context interceptor, so a guessed UUID read any tenant's file), no
 * {@code Range} support (so video — a named use case — could not be seeked),
 * {@code Cache-Control: public, max-age=86400} on tenant documents, no
 * {@code Content-Disposition}, and unbounded concurrent streams off local disk.
 */
@RestController
@RequestMapping(ApiPaths.MEDIA_SERVE)
@Tag(name = "Media (local serve)", description = "Development-profile file streaming")
@Slf4j
public class MediaServeController {

    /** Bounded concurrency: unlimited streams off one disk is a DoS surface. */
    private static final int MAX_CONCURRENT_STREAMS = 32;

    private final StoragePort storage;
    private final MediaRepositoryPort mediaRepository;
    private final MediaAccessGuard guard;
    private final AuditPort audit;
    private final Semaphore streamPermits = new Semaphore(MAX_CONCURRENT_STREAMS);

    public MediaServeController(StoragePort storage, MediaRepositoryPort mediaRepository,
                                MediaAccessGuard guard, AuditPort audit) {
        this.storage = storage;
        this.mediaRepository = mediaRepository;
        this.guard = guard;
        this.audit = audit;
    }

    @GetMapping("/**")
    @Operation(summary = "Stream a stored object (local provider only)")
    public ResponseEntity<Resource> serve(HttpServletRequest request,
                                          @org.springframework.web.bind.annotation.RequestHeader(
                                                  value = HttpHeaders.RANGE, required = false) String rangeHeader,
                                          @org.springframework.web.bind.annotation.RequestHeader(
                                                  value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch)
            throws IOException {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_READ);

        StorageKey key = extractKey(request);

        // The ownership check the predecessor never had. A leaked or guessed key
        // is useless without a token for the owning tenant.
        Media media = mediaRepository.findByStorageKeyForTenant(key, principal.tenant())
                .orElseThrow(() -> new MediaNotFoundException("no media at key for " + principal.tenant()));
        if (!media.isReadable()) {
            throw new MediaNotFoundException("media " + media.id() + " is " + media.status());
        }
        guard.requireKeyOwnership(media, principal);

        StoragePort.StoredObject object = storage.head(key)
                .orElseThrow(() -> new MediaNotFoundException("object missing for media " + media.id()));

        String etag = buildETag(object, media);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        if (!streamPermits.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "5").build();
        }
        try {
            audit.record(principal.tenant(), principal.asActor(clientIp()),
                    AuditPort.AuditAction.MEDIA_DOWNLOADED, media.id().asString(), null);
            return rangeHeader == null
                    ? full(media, object, etag)
                    : partial(media, object, etag, rangeHeader);
        } finally {
            streamPermits.release();
        }
    }

    private ResponseEntity<Resource> full(Media media, StoragePort.StoredObject object, String etag) {
        InputStream stream = storage.read(media.storageKey());
        return applyHeaders(ResponseEntity.ok(), media, object, etag)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(object.size().value())
                .body(new InputStreamResource(stream));
    }

    /** HTTP 206. Required for video seeking, and entirely absent before. */
    private ResponseEntity<Resource> partial(Media media, StoragePort.StoredObject object,
                                             String etag, String rangeHeader) {
        long total = object.size().value();
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total).build();
        }
        if (ranges.size() != 1) {
            // Multi-range responses need multipart/byteranges; no client needs it here.
            return full(media, object, etag);
        }
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(total);
        long end = range.getRangeEnd(total);
        int length = (int) Math.min(end - start + 1, Integer.MAX_VALUE);

        byte[] slice = storage.readRange(media.storageKey(), start, length);
        return applyHeaders(ResponseEntity.status(HttpStatus.PARTIAL_CONTENT), media, object, etag)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total)
                .contentLength(slice.length)
                .body(new ByteArrayResource(slice));
    }

    private String buildETag(StoragePort.StoredObject object, Media media) {
        String raw = object.etag() != null ? object.etag()
                : media.checksum() != null ? media.checksum().sha256Hex()
                : String.valueOf(media.billableSize().value());
        return raw.startsWith("\"") ? raw : "\"" + raw + "\"";
    }

    private ResponseEntity.BodyBuilder applyHeaders(ResponseEntity.BodyBuilder builder, Media media,
                                                    StoragePort.StoredObject object, String etag) {
        String filename = media.originalFilename();
        return builder
                .eTag(etag)
                // private, not public: the predecessor let any intermediate proxy
                // cache a tenant's document for 24 hours with no invalidation path.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_TYPE, object.contentType())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                // attachment: no uploaded file ever renders inline in a browser
                // origin. This is the practical mitigation for stored XSS.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(URLEncoder.encode(filename, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                        .build().toString());
    }

    private StorageKey extractKey(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = ApiPaths.MEDIA_SERVE + "/";
        String raw = path != null && path.startsWith(prefix) ? path.substring(prefix.length()) : null;
        if (raw == null || raw.isBlank()) {
            throw new MediaNotFoundException("no storage key in serve path");
        }
        try {
            // StorageKey's own constructor rejects traversal sequences.
            return new StorageKey(raw);
        } catch (IllegalArgumentException e) {
            log.warn("rejected serve path: {}", e.getMessage());
            throw new MediaNotFoundException("illegal storage key in serve path");
        }
    }

    private String clientIp() {
        var ctx = RequestContext.get();
        return ctx == null ? null : ctx.clientIp();
    }
}
