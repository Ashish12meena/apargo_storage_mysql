package com.aigreentick.services.storage.api.v1.media;

import com.aigreentick.services.storage.api.v1.media.dto.request.BatchDeleteRequest;
import com.aigreentick.services.storage.api.v1.media.dto.request.CompleteUploadRequest;
import com.aigreentick.services.storage.api.v1.media.dto.request.InitiateUploadRequest;
import com.aigreentick.services.storage.api.common.Responses;
import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.api.common.validation.OneOf;
import com.aigreentick.services.storage.api.security.RequiresIdempotencyKey;
import com.aigreentick.services.storage.api.v1.media.dto.response.BatchDeleteResponse;
import com.aigreentick.services.storage.api.v1.media.dto.response.BatchItemResult;
import com.aigreentick.services.storage.api.v1.media.dto.response.DownloadUrlResponse;
import com.aigreentick.services.storage.api.v1.media.dto.response.BatchUploadResponse;
import com.aigreentick.services.storage.api.v1.media.dto.response.MediaResponse;
import com.aigreentick.services.storage.api.common.dto.response.PageResponse;
import com.aigreentick.services.storage.api.v1.media.dto.response.UploadTicketResponse;
import com.aigreentick.services.storage.api.v1.media.mapper.MediaDtoMapper;
import com.aigreentick.services.storage.api.security.MediaAccessGuard;
import com.aigreentick.services.storage.api.security.Scope;
import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.api.security.TenantPrincipal;
import com.aigreentick.services.storage.application.port.in.command.BatchProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.CompleteUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.DeleteMediaCommand;
import com.aigreentick.services.storage.application.port.in.command.InitiateUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.ProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.RestoreMediaCommand;
import com.aigreentick.services.storage.application.port.in.BatchUploadMediaUseCase;
import com.aigreentick.services.storage.application.port.in.DeleteMediaUseCase;
import com.aigreentick.services.storage.application.port.in.QueryMediaUseCase;
import com.aigreentick.services.storage.application.port.in.UploadMediaUseCase;
import com.aigreentick.services.storage.application.port.in.result.BatchUploadView;
import com.aigreentick.services.storage.application.shared.MediaListQuery;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.domain.exception.DomainException;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.MediaType;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code /api/v1/media} — tenant-facing operations.
 *
 * <p>Follows the company API Standard: every JSON response is the
 * {@link ApiResponse} wrapper built through {@link Responses}; creates are
 * {@code 201} + {@code Location} and require {@code X-Idempotency-Key}; batch
 * routes are {@code 200} with per-item results in {@code data}; the list uses
 * cursor paging ({@code size}, {@code cursor}).
 *
 * <p>Constraints this class honours:
 * <ul>
 *   <li>Tenant comes from the verified {@link TenantPrincipal}. It never reads a
 *       tenant header.</li>
 *   <li>One use-case call per route. No orchestration, no repository access, no
 *       conditional expressing a business rule.</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.MEDIA)
@Tag(name = "Media", description = "Tenant-facing media operations")
@Slf4j
public class MediaController {

    private static final String MULTIPART = "multipart/form-data";
    private static final Duration MAX_URL_TTL = Duration.ofHours(1);
    private static final Duration DEFAULT_URL_TTL = Duration.ofMinutes(15);

    private final UploadMediaUseCase uploadUseCase;
    private final BatchUploadMediaUseCase batchUploadUseCase;
    private final QueryMediaUseCase queryUseCase;
    private final DeleteMediaUseCase deleteUseCase;
    private final MediaAccessGuard guard;
    private final MediaDtoMapper mapper;

    public MediaController(UploadMediaUseCase uploadUseCase, BatchUploadMediaUseCase batchUploadUseCase,
                           QueryMediaUseCase queryUseCase, DeleteMediaUseCase deleteUseCase,
                           MediaAccessGuard guard, MediaDtoMapper mapper) {
        this.uploadUseCase = uploadUseCase;
        this.batchUploadUseCase = batchUploadUseCase;
        this.queryUseCase = queryUseCase;
        this.deleteUseCase = deleteUseCase;
        this.guard = guard;
        this.mapper = mapper;
    }

    // ────────────────────────────── UPLOAD ──────────────────────────────

    @RequiresIdempotencyKey
    @PostMapping(path = "/upload", consumes = MULTIPART)
    @Operation(summary = "Upload a small file through the service",
            description = "201 Created with Location: /api/v1/media/{id}. Requires X-Idempotency-Key.")
    public ResponseEntity<ApiResponse<MediaResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        // Entry marker. Reaching this line proves the filter chain passed, the
        // multipart part named "file" was resolved, and argument binding
        // succeeded — so a 400/422 with NO such line means the request
        // failed during argument resolution (missing part, missing/unparseable
        // header) and never entered the controller at all.
        log.info("upload received: filename={} declaredType={} bytes={} idempotencyKey={}",
                file.getOriginalFilename(), file.getContentType(), file.getSize(),
                idempotencyKey == null ? "<none>" : idempotencyKey);

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_WRITE);

        log.info("upload authorised: org={} project={} actor={}",
                principal.tenant().orgId(), principal.tenant().projectId(), principal.userId());

        MediaView view = uploadUseCase.uploadProxied(new ProxiedUploadCommand(
                principal.tenant(), actor(principal), file.getOriginalFilename(),
                file.getContentType(), ByteSize.of(file.getSize()),
                () -> openStream(file), idempotencyKey));

        return Responses.created(URI.create(ApiPaths.mediaLocation(view.id())),
                "Upload complete", mapper.toResponse(view));
    }

    /**
     * Many small files in ONE request, for {@code template-service}, which
     * downloads media in batches and would otherwise open one connection per file.
     *
     * <p>Returns {@code 200} whenever the batch was processed, including mixed
     * results: the standard's "action done, with a result" row. Per-file outcomes
     * live in {@code data.results[]} (unchanged shape), so a client must read
     * {@code failedCount}, never infer per-file success from the HTTP status.
     * (Pre-standard this was {@code 207}; every 2xx client, template-service
     * included, is unaffected.)
     *
     * <p>Request-level rejections (no files, too many files) are NOT batch results:
     * they leave through the global exception handler with the standard envelope.
     * Per-file failures are results.
     */
    @RequiresIdempotencyKey
    @PostMapping(path = "/upload/batch", consumes = MULTIPART)
    @Operation(summary = "Upload many small files in one request; per-file partial success",
            description = "200 with data {successCount, failedCount, results[]}. Requires X-Idempotency-Key "
                    + "(per-file keys K:0, K:1, ... are derived from it).")
    public ResponseEntity<ApiResponse<BatchUploadResponse>> uploadBatch(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_WRITE);

        // required=false, then checked in the use case: a missing part must produce
        // the service's own BATCH_FILES_REQUIRED envelope rather than Spring's
        // MissingServletRequestPartException, which names an internal parameter.
        List<BatchProxiedUploadCommand.FileItem> items = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                items.add(new BatchProxiedUploadCommand.FileItem(
                        file.getOriginalFilename(), file.getContentType(),
                        ByteSize.of(file.getSize()), () -> openStream(file)));
            }
        }

        BatchUploadView view = batchUploadUseCase.uploadBatch(new BatchProxiedUploadCommand(
                principal.tenant(), actor(principal), items, idempotencyKey));

        return Responses.ok("Batch upload processed", mapper.toResponse(view));
    }

    /**
     * Streams directly from the multipart part. The predecessor spooled every
     * upload to a temp file first, costing a full extra disk write and read.
     */
    private InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read uploaded part", e);
        }
    }

    @RequiresIdempotencyKey
    @PostMapping("/uploads")
    @Operation(summary = "Initiate a direct-to-storage upload and receive presigned URL(s)",
            description = "201 Created with Location: /api/v1/media/uploads/{uploadId}. Requires X-Idempotency-Key.")
    public ResponseEntity<ApiResponse<UploadTicketResponse>> initiate(
            @Valid @RequestBody InitiateUploadRequest request,
            @RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_WRITE);

        var ticket = uploadUseCase.initiate(new InitiateUploadCommand(
                principal.tenant(), actor(principal), request.filename(),
                request.declaredContentType(), ByteSize.of(request.sizeBytes()), idempotencyKey));

        UploadTicketResponse response = mapper.toResponse(ticket);
        return Responses.created(URI.create(ApiPaths.uploadSessionLocation(response.uploadId())),
                "Upload initiated", response);
    }

    @PostMapping("/uploads/{uploadId}/complete")
    @Operation(summary = "Confirm a direct upload; validates before the record becomes readable")
    public ResponseEntity<ApiResponse<MediaResponse>> complete(
            @PathVariable String uploadId,
            @Valid @RequestBody(required = false) CompleteUploadRequest request) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_WRITE);

        List<CompleteUploadCommand.PartETag> parts = request == null ? List.of()
                : request.parts().stream()
                        .map(p -> new CompleteUploadCommand.PartETag(p.partNumber(), p.etag()))
                        .toList();

        MediaView view = uploadUseCase.complete(new CompleteUploadCommand(
                principal.tenant(), actor(principal), uploadId, parts));

        return Responses.ok("Upload complete", mapper.toResponse(view));
    }

    @DeleteMapping("/uploads/{uploadId}")
    @Operation(summary = "Abandon an initiated upload and release its quota")
    public ResponseEntity<Void> abortUpload(@PathVariable String uploadId) {
        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_WRITE);
        uploadUseCase.abort(uploadId, principal.tenant());
        return Responses.noContent();
    }

    // ────────────────────────────── READ ──────────────────────────────

    /**
     * Cursor paging (API Standard §5): {@code size} 1–100 (default 20) and the
     * opaque {@code cursor} from the previous page's
     * {@code pagination.nextCursor}. Out-of-range {@code size} or an unknown
     * {@code type} is 422.
     */
    @GetMapping
    @Operation(summary = "List media with cursor pagination",
            description = "data = {items, pagination: {size, nextCursor, hasNext}}. Omit cursor for the first page.")
    public ResponseEntity<ApiResponse<PageResponse<MediaResponse>>> list(
            @RequestParam(required = false)
            @OneOf(value = {"IMAGE", "VIDEO", "AUDIO", "DOCUMENT"}, ignoreCase = true) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + MediaListQuery.DEFAULT_LIMIT)
            @Min(1) @Max(MediaListQuery.MAX_LIMIT) int size) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_READ);

        MediaListQuery query = new MediaListQuery(principal.tenant(), MediaType.fromValue(type), cursor, size);
        var page = queryUseCase.list(query);

        return Responses.ok("Media fetched", mapper.toPageResponse(page, query.limit()));
    }

    @GetMapping("/{mediaId}")
    @Operation(summary = "Fetch metadata for a single media item")
    public ResponseEntity<ApiResponse<MediaResponse>> getById(@PathVariable String mediaId) {
        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_READ);
        MediaView view = queryUseCase.getById(MediaId.parse(mediaId), principal.tenant());
        return Responses.ok("Media fetched", mapper.toResponse(view));
    }

    /**
     * Replaces {@code GET /media/public-url?storageKey=...}, which accepted a
     * client-supplied storage key with no ownership check.
     */
    @GetMapping("/{mediaId}/download-url")
    @Operation(summary = "Mint a short-lived, tenant-scoped download URL")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> downloadUrl(
            @PathVariable String mediaId,
            @RequestParam(defaultValue = "900") long ttlSeconds) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_READ);

        Duration ttl = clampTtl(ttlSeconds);
        String url = queryUseCase.generateDownloadUrl(MediaId.parse(mediaId), principal.tenant(), ttl);

        return Responses.ok("Download URL issued", new DownloadUrlResponse(url, Instant.now().plus(ttl)));
    }

    private Duration clampTtl(long requestedSeconds) {
        if (requestedSeconds <= 0) {
            return DEFAULT_URL_TTL;
        }
        Duration requested = Duration.ofSeconds(requestedSeconds);
        return requested.compareTo(MAX_URL_TTL) > 0 ? MAX_URL_TTL : requested;
    }

    // ────────────────────────────── DELETE ──────────────────────────────

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Soft-delete a media item and release its quota")
    public ResponseEntity<Void> delete(
            @PathVariable String mediaId,
            @RequestParam(defaultValue = "false") boolean permanent) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_DELETE);
        if (permanent) {
            guard.requireScope(principal, Scope.MEDIA_DELETE_PERMANENT);
        }
        deleteUseCase.delete(new DeleteMediaCommand(
                MediaId.parse(mediaId), principal.tenant(), actor(principal), permanent));
        return Responses.noContent();
    }

    @PostMapping("/{mediaId}/restore")
    @Operation(summary = "Restore a soft-deleted item within the grace period")
    public ResponseEntity<ApiResponse<MediaResponse>> restore(@PathVariable String mediaId) {
        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_DELETE);
        MediaView view = deleteUseCase.restore(new RestoreMediaCommand(
                MediaId.parse(mediaId), principal.tenant(), actor(principal)));
        return Responses.ok("Restored", mapper.toResponse(view));
    }

    /**
     * Partial success is normal: one bad id never fails the batch. {@code 200}
     * with {@code data = {successCount, failedCount, results[]}} (was a bare
     * array under {@code 207}).
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete up to 100 items, reporting per-item results")
    public ResponseEntity<ApiResponse<BatchDeleteResponse>> deleteBatch(
            @Valid @RequestBody BatchDeleteRequest request) {

        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.MEDIA_DELETE);

        List<BatchItemResult> results = new ArrayList<>(request.mediaIds().size());
        for (String rawId : request.mediaIds()) {
            try {
                deleteUseCase.delete(new DeleteMediaCommand(
                        MediaId.parse(rawId), principal.tenant(), actor(principal), false));
                results.add(BatchItemResult.ok(rawId));
            } catch (DomainException e) {
                results.add(BatchItemResult.failed(rawId, e.errorCode().name(), e.clientMessage()));
            } catch (IllegalArgumentException e) {
                results.add(BatchItemResult.failed(rawId, ErrorCode.VALIDATION_FAILED.name(), "Malformed media id."));
            }
        }
        return Responses.ok("Batch delete processed", BatchDeleteResponse.of(results));
    }

    private Actor actor(TenantPrincipal principal) {
        var ctx = RequestContext.get();
        return principal.asActor(ctx == null ? null : ctx.clientIp());
    }
}