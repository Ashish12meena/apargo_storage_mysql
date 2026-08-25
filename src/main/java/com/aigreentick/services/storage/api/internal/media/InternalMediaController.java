package com.aigreentick.services.storage.api.internal.media;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.api.security.MediaAccessGuard;
import com.aigreentick.services.storage.api.security.Scope;
import com.aigreentick.services.storage.api.security.TenantContext;
import com.aigreentick.services.storage.application.port.in.command.TeardownTenantCommand;
import com.aigreentick.services.storage.application.port.in.TeardownTenantUseCase;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.domain.shared.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /internal/media} — tenant offboarding.
 *
 * <p>Both routes are ASYNCHRONOUS and return {@code 202 Accepted}. An organisation
 * teardown can span millions of objects; running it inside a request would hold a
 * connection and a thread for as long as it takes and fail with no way to resume.
 *
 * <p>Requires {@code tenant:teardown}, deliberately NOT implied by
 * {@code media:delete}: removing one file and wiping a customer's entire library
 * are different blast radii and should need different credentials.
 *
 * <p>Unlike the predecessor's {@code deleteByOrgAndProject} and
 * {@code deleteByOrganisation}, these release quota and remove the stored objects.
 */
@RestController
@RequestMapping(ApiPaths.INTERNAL_MEDIA)
@Tag(name = "Internal — Media lifecycle", description = "Tenant offboarding")
public class InternalMediaController {

    private final TeardownTenantUseCase teardownUseCase;
    private final MediaAccessGuard guard;

    public InternalMediaController(TeardownTenantUseCase teardownUseCase, MediaAccessGuard guard) {
        this.teardownUseCase = teardownUseCase;
        this.guard = guard;
    }

    @DeleteMapping("/project/{orgId}/{projectId}")
    @Operation(summary = "Remove every file in a project, asynchronously")
    public ResponseEntity<ApiResponse<Map<String, Object>>> teardownProject(
            @PathVariable long orgId,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "false") boolean permanent) {

        return accept(new TeardownTenantCommand(orgId, projectId, permanent, actor()), "PROJECT");
    }

    /**
     * Whole-organisation teardown — every project under it.
     *
     * <p>{@code permanent=true} skips the grace period. Use it for compliance
     * erasure; leave it false for cancellations, which are the ones people reverse.
     */
    @DeleteMapping("/org/{orgId}")
    @Operation(summary = "Remove every file in an organisation, asynchronously")
    public ResponseEntity<ApiResponse<Map<String, Object>>> teardownOrg(
            @PathVariable long orgId,
            @RequestParam(defaultValue = "false") boolean permanent) {

        return accept(new TeardownTenantCommand(orgId, null, permanent, actor()), "ORG");
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> accept(TeardownTenantCommand command,
                                                                    String scope) {
        guard.requireScope(TenantContext.require(), Scope.TENANT_TEARDOWN);
        String handle = teardownUseCase.requestTeardown(command);

        // 202, not 200: the work has been accepted, not performed. Reporting
        // success before the files are gone would be a lie a compliance auditor
        // could act on.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                "Teardown accepted; processing asynchronously",
                Map.of("handle", handle,
                        "scope", scope,
                        "permanent", command.permanent(),
                        "status", "ACCEPTED"),
                RequestContext.traceIdOrNull()));
    }

    private Actor actor() {
        var principal = TenantContext.getOrNull();
        var ctx = RequestContext.get();
        String ip = ctx == null ? null : ctx.clientIp();
        return principal == null
                ? new Actor("unknown-service", Actor.ActorType.SERVICE, ip)
                : new Actor(principal.userId(), Actor.ActorType.SERVICE, ip);
    }
}
