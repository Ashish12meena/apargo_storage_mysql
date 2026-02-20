package com.aigreentick.services.storage.controller.v1;


import com.aigreentick.services.storage.domain.OrgStorage;
import com.aigreentick.services.storage.domain.ProjectStorage;
import com.aigreentick.services.storage.dto.response.ApiResponse;
import com.aigreentick.services.storage.service.impl.quota.QuotaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal API for the Organisation Service to provision and manage
 * storage quotas. Not exposed to end-users.
 */
@Slf4j
@RestController
@RequestMapping("/internal/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    // ── Org Quota ───────────────────────────────────────────────────────────

    @PutMapping("/org")
    public ResponseEntity<ApiResponse<OrgQuotaResponse>> upsertOrgQuota(
            @Valid @RequestBody OrgQuotaRequest request) {

        log.info("Upsert org quota: orgId={} maxBytes={}", request.getOrgId(), request.getMaxBytes());
        OrgStorage saved = quotaService.upsertOrgQuota(request.getOrgId(), request.getMaxBytes());

        return ResponseEntity.ok(ApiResponse.success("Org quota provisioned",
                new OrgQuotaResponse(saved.getOrgId(), saved.getMaxBytes(), saved.getUsedBytes())));
    }

    @GetMapping("/org/{orgId}")
    public ResponseEntity<ApiResponse<OrgQuotaResponse>> getOrgQuota(@PathVariable Long orgId) {
        OrgStorage org = quotaService.getOrgQuota(orgId);
        if (org == null) {
            return ResponseEntity.ok(ApiResponse.error("Org quota not found for org=" + orgId));
        }
        return ResponseEntity.ok(ApiResponse.success(
                new OrgQuotaResponse(org.getOrgId(), org.getMaxBytes(), org.getUsedBytes())));
    }

    // ── Project Quota ───────────────────────────────────────────────────────

    @PutMapping("/project")
    public ResponseEntity<ApiResponse<ProjectQuotaResponse>> upsertProjectQuota(
            @Valid @RequestBody ProjectQuotaRequest request) {

        log.info("Upsert project quota: orgId={} projectId={} maxBytes={}",
                request.getOrgId(), request.getProjectId(), request.getMaxBytes());
        ProjectStorage saved = quotaService.upsertProjectQuota(
                request.getOrgId(), request.getProjectId(), request.getMaxBytes());

        return ResponseEntity.ok(ApiResponse.success("Project quota provisioned",
                new ProjectQuotaResponse(saved.getOrgId(), saved.getProjectId(),
                        saved.getMaxBytes(), saved.getUsedBytes())));
    }

    @GetMapping("/project/{orgId}/{projectId}")
    public ResponseEntity<ApiResponse<ProjectQuotaResponse>> getProjectQuota(
            @PathVariable Long orgId, @PathVariable Long projectId) {

        ProjectStorage proj = quotaService.getProjectQuota(orgId, projectId);
        if (proj == null) {
            return ResponseEntity.ok(ApiResponse.error(
                    "Project quota not found for org=" + orgId + " project=" + projectId));
        }
        return ResponseEntity.ok(ApiResponse.success(
                new ProjectQuotaResponse(proj.getOrgId(), proj.getProjectId(),
                        proj.getMaxBytes(), proj.getUsedBytes())));
    }

    // ── Request / Response DTOs ─────────────────────────────────────────────

    @Data
    public static class OrgQuotaRequest {
        @NotNull private Long orgId;
        @NotNull @Min(0) private Long maxBytes;
    }

    @Data
    public static class ProjectQuotaRequest {
        @NotNull private Long orgId;
        @NotNull private Long projectId;
        @NotNull @Min(0) private Long maxBytes;
    }

    public record OrgQuotaResponse(Long orgId, Long maxBytes, Long usedBytes) {}
    public record ProjectQuotaResponse(Long orgId, Long projectId, Long maxBytes, Long usedBytes) {}
}