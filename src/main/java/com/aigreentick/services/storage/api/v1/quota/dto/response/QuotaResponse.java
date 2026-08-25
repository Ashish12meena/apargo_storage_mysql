package com.aigreentick.services.storage.api.v1.quota.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotaResponse(Long orgId, Long projectId, long maxBytes, long usedBytes,
                            long remainingBytes, double utilisation) {
}
