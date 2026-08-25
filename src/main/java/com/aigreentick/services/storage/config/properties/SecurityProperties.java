package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * {@code security.*}
 *
 * <p>Authentication is a shared API key per calling service (ADR-010, revised).
 * The key identifies WHO is calling; the tenant headers say WHICH tenant the call
 * is for.
 *
 * <p>Understand the boundary this gives you: an authenticated caller may assert
 * ANY tenant. That is acceptable while every caller is a trusted internal service,
 * and it is a large improvement on trusting headers from anyone who can reach the
 * port. It does NOT protect against a compromised or buggy caller passing the
 * wrong organisation id.
 *
 * @param apiKeyEnabled   may be false in dev only; production startup fails if it
 *                        is false, because a control that can be switched off in
 *                        production is not a control
 * @param clients         one entry per calling service. Each carries its own key
 *                        and its own scopes, so a key can be rotated or revoked
 *                        without touching the others.
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @DefaultValue("true") boolean apiKeyEnabled,
        @DefaultValue("X-Api-Key") String apiKeyHeader,
        List<Client> clients) {

    public SecurityProperties {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    /**
     * @param id     caller name, recorded in the audit trail and in logs
     * @param key    the shared secret. NEVER a literal in YAML — always an
     *               environment variable resolved from a secret store.
     * @param scopes what this caller may do: media:read, media:write,
     *               media:delete, media:delete:permanent, quota:read, quota:admin
     * @param fixedOrgId   optional. When set, this caller may ONLY act for this
     *                     organisation and the X-Org-Id header is ignored. Use for
     *                     single-tenant integrations — it removes the
     *                     confused-deputy risk entirely for that caller.
     * @param fixedProjectId optional, same idea at project scope.
     */
    public record Client(String id, String key, List<String> scopes,
                         Long fixedOrgId, Long fixedProjectId) {

        public Client {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        public boolean isPinnedToTenant() {
            return fixedOrgId != null;
        }
    }
}