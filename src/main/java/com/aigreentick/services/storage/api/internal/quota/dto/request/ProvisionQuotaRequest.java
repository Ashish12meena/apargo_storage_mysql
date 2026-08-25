package com.aigreentick.services.storage.api.internal.quota.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Shape preserved exactly from the predecessor's internal API. Only the
 * authentication requirement changes.
 *
 * @param projectId null for org-scope provisioning
 */
public record ProvisionQuotaRequest(
        @NotNull @Min(1) Long orgId,
        @Min(1) Long projectId,
        @NotNull @Min(0) Long maxBytes) {
}
