package com.aigreentick.services.storage.api.v1.media.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bounded batch; partial success is reported per item. */
public record BatchDeleteRequest(
        @NotEmpty @Size(max = 100) List<String> mediaIds) {
}
