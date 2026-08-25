package com.aigreentick.services.storage.api.v1.quota;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.api.v1.quota.dto.response.QuotaResponse;
import com.aigreentick.services.storage.api.v1.quota.mapper.QuotaDtoMapper;
import com.aigreentick.services.storage.api.security.MediaAccessGuard;
import com.aigreentick.services.storage.api.security.Scope;
import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.api.security.TenantPrincipal;
import com.aigreentick.services.storage.application.port.in.ManageQuotaUseCase;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.context.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/quota} — a tenant reading its OWN quota.
 *
 * <p>New. The predecessor exposed quota only through the internal admin API, so a
 * tenant could not see usage and discovered the limit by hitting it. Scoped to the
 * caller's own tenant with no id parameter, so there is nothing to tamper with.
 */
@RestController
@RequestMapping(ApiPaths.QUOTA)
@Tag(name = "Quota", description = "Tenant self-service quota")
public class QuotaController {

    private final ManageQuotaUseCase quotaUseCase;
    private final MediaAccessGuard guard;
    private final QuotaDtoMapper mapper;

    public QuotaController(ManageQuotaUseCase quotaUseCase, MediaAccessGuard guard, QuotaDtoMapper mapper) {
        this.quotaUseCase = quotaUseCase;
        this.guard = guard;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Read the calling tenant's storage quota and utilisation")
    public ResponseEntity<ApiResponse<QuotaResponse>> getOwnQuota() {
        TenantPrincipal principal = TenantContext.require();
        guard.requireScope(principal, Scope.QUOTA_READ);
        return ResponseEntity.ok(ApiResponse.success(null,
                mapper.toResponse(quotaUseCase.getForTenant(principal.tenant())),
                RequestContext.traceIdOrNull()));
    }
}
