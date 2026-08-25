package com.aigreentick.services.storage.api.v1.quota.mapper;

import com.aigreentick.services.storage.api.v1.quota.dto.response.QuotaResponse;
import com.aigreentick.services.storage.application.port.in.result.QuotaView;
import org.springframework.stereotype.Component;

/**
 * Quota domain view to wire shape. Hand-written and explicit, like every mapper
 * in this project.
 *
 * <p>Split out of {@code MediaDtoMapper} when the API layer was organised by
 * module: a quota mapping living in a media mapper meant every quota route
 * depended on the media DTO package.
 *
 * <p>Shared deliberately between {@code /api/v1/quota} and {@code /internal/quota}.
 * Those two differ in their TENANCY MODEL — who the caller is acting for — not in
 * how a quota is represented. Duplicating the mapping so each side owned one would
 * let the two representations drift apart for no benefit.
 */
@Component
public class QuotaDtoMapper {

    public QuotaResponse toResponse(QuotaView view) {
        return new QuotaResponse(view.orgId(), view.projectId(), view.maxBytes(),
                view.usedBytes(), view.remainingBytes(), view.utilisation());
    }
}
