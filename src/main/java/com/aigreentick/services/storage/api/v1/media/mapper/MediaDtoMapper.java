package com.aigreentick.services.storage.api.v1.media.mapper;

import com.aigreentick.services.storage.api.v1.media.dto.response.BatchUploadResponse;
import com.aigreentick.services.storage.api.v1.media.dto.response.MediaResponse;
import com.aigreentick.services.storage.api.common.dto.response.PageResponse;
import com.aigreentick.services.storage.api.v1.media.dto.response.UploadTicketResponse;
import com.aigreentick.services.storage.application.port.in.result.BatchUploadView;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.application.shared.PageView;
import com.aigreentick.services.storage.application.port.in.result.UploadTicket;
import org.springframework.stereotype.Component;

/**
 * View → DTO. Hand-written and explicit: field-by-field mapping is verbose but
 * makes an unintended field exposure visible in review. Automatic mapping is how a
 * storage key ends up in a response nobody meant to widen.
 */
@Component
public class MediaDtoMapper {

    public MediaResponse toResponse(MediaView view) {
        return new MediaResponse(
                view.id(),
                view.originalFilename(),
                view.id(),                 // storedFilename: the ID, never the storage key
                view.contentType(),
                view.mediaType(),
                view.sizeBytes(),
                view.status(),
                view.checksumSha256(),
                view.downloadUrl(),
                view.downloadUrlExpiresAt(),
                view.createdAt(),
                view.createdBy());
    }

    public PageResponse<MediaResponse> toPageResponse(PageView<MediaView> page) {
        return new PageResponse<>(page.items().stream().map(this::toResponse).toList(),
                page.nextCursor(), page.hasMore());
    }

    /**
     * Field-by-field, like every other mapping here. A batch result is exactly
     * where an automatic mapper would quietly widen the response — the failure
     * entries carry an exception-derived message, and only the CLIENT-safe side of
     * that distinction may cross this boundary.
     */
    public BatchUploadResponse toResponse(BatchUploadView view) {
        return new BatchUploadResponse(
                view.successCount(),
                view.failedCount(),
                view.results().stream().map(this::toItem).toList());
    }

    /**
     * Flattens the read model into a batch entry.
     *
     * <p>Field-by-field rather than delegating to {@code toResponse(MediaView)}:
     * the batch entry exposes a deliberately NARROWER set than single-file upload.
     * It omits the storage-oriented fields — checksum, status, URL expiry,
     * uploader — because {@code template-service} does not consume them, and a
     * batch response is precisely where an automatic mapper would widen the
     * payload without anyone noticing.
     */
    private BatchUploadResponse.Item toItem(BatchUploadView.ItemView item) {
        if (item.media() == null) {
            return BatchUploadResponse.Item.failure(item.originalFilename(),
                    item.status().name(), item.errorCode(), item.errorMessage());
        }
        var media = item.media();
        return BatchUploadResponse.Item.success(item.originalFilename(),
                media.downloadUrl(), media.mediaType(), media.contentType(), media.sizeBytes());
    }

    public UploadTicketResponse toResponse(UploadTicket ticket) {
        return new UploadTicketResponse(ticket.uploadSessionId(), ticket.mediaId(), ticket.mode(),
                ticket.urls(), ticket.requiredHeaders(), ticket.partSizeBytes(), ticket.expiresAt());
    }
}