package com.aigreentick.services.storage.api.internal.quota;

import com.aigreentick.services.storage.api.internal.quota.dto.request.ProvisionQuotaRequest;
import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.api.v1.quota.dto.response.QuotaResponse;
import com.aigreentick.services.storage.api.v1.quota.mapper.QuotaDtoMapper;
import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.application.port.in.command.ProvisionQuotaCommand;
import com.aigreentick.services.storage.application.port.in.ManageQuotaUseCase;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.domain.quota.QuotaScope;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /internal/quota} — provisioning, called by the organisation service.
 *
 * <p>Request and response shapes are preserved exactly from the predecessor. Only
 * the authentication requirement changes, and that is enforced upstream by
 * {@code InternalCallerFilter} — this surface previously had none at all.
 */
@RestController
@RequestMapping(ApiPaths.INTERNAL_QUOTA)
@Tag(name = "Internal — Quota", description = "Service-to-service quota provisioning")
public class InternalQuotaController {

    private final ManageQuotaUseCase quotaUseCase;
    private final QuotaDtoMapper mapper;

    public InternalQuotaController(ManageQuotaUseCase quotaUseCase, QuotaDtoMapper mapper) {
        this.quotaUseCase = quotaUseCase;
        this.mapper = mapper;
    }

    @PutMapping("/org")
    @Operation(summary = "Create or update an organisation's storage limit (idempotent)")
    public ResponseEntity<ApiResponse<QuotaResponse>> upsertOrgQuota(
            @Valid @RequestBody ProvisionQuotaRequest request) {

        var view = quotaUseCase.provision(new ProvisionQuotaCommand(
                QuotaScope.ORG, request.orgId(), null, ByteSize.of(request.maxBytes()), serviceActor()));
        return ResponseEntity.ok(ApiResponse.success("Org quota provisioned",
                mapper.toResponse(view), RequestContext.traceIdOrNull()));
    }

    @PutMapping("/project")
    @Operation(summary = "Create or update a project's storage limit (idempotent)")
    public ResponseEntity<ApiResponse<QuotaResponse>> upsertProjectQuota(
            @Valid @RequestBody ProvisionQuotaRequest request) {

        var view = quotaUseCase.provision(new ProvisionQuotaCommand(
                QuotaScope.PROJECT, request.orgId(), request.projectId(),
                ByteSize.of(request.maxBytes()), serviceActor()));
        return ResponseEntity.ok(ApiResponse.success("Project quota provisioned",
                mapper.toResponse(view), RequestContext.traceIdOrNull()));
    }

    @GetMapping("/org/{orgId}")
    @Operation(summary = "Read an organisation's quota")
    public ResponseEntity<ApiResponse<QuotaResponse>> getOrgQuota(@PathVariable long orgId) {
        return ResponseEntity.ok(ApiResponse.success(null,
                mapper.toResponse(quotaUseCase.getOrgQuota(orgId)), RequestContext.traceIdOrNull()));
    }

    @GetMapping("/project/{orgId}/{projectId}")
    @Operation(summary = "Read a project's quota")
    public ResponseEntity<ApiResponse<QuotaResponse>> getProjectQuota(
            @PathVariable long orgId, @PathVariable long projectId) {
        return ResponseEntity.ok(ApiResponse.success(null,
                mapper.toResponse(quotaUseCase.getProjectQuota(new TenantRef(orgId, projectId))),
                RequestContext.traceIdOrNull()));
    }

    private Actor serviceActor() {
        var principal = TenantContext.getOrNull();
        var ctx = RequestContext.get();
        String ip = ctx == null ? null : ctx.clientIp();
        return principal == null
                ? new Actor("unknown-service", Actor.ActorType.SERVICE, ip)
                : new Actor(principal.userId(), Actor.ActorType.SERVICE, ip);
    }
}
