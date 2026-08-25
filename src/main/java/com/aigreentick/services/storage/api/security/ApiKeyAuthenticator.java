package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.config.properties.SecurityProperties;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves an API key to a caller identity, then builds the {@link TenantPrincipal}
 * from that identity plus the tenant headers.
 *
 * <p>Sits behind the same seam JWT verification used, so swapping back to token
 * verification later is one class and no change to controllers or services.
 */
@Component
@Slf4j
public class ApiKeyAuthenticator {

    private final SecurityProperties properties;
    private final Map<String, ResolvedClient> byKey = new LinkedHashMap<>();

    public ApiKeyAuthenticator(SecurityProperties properties) {
        this.properties = properties;
        for (SecurityProperties.Client client : properties.clients()) {
            if (client.key() == null || client.key().isBlank()) {
                throw new IllegalStateException(
                        "security.clients[" + client.id() + "].key is not set");
            }
            if (client.key().length() < 32) {
                // A short shared secret is brute-forceable, and the whole model
                // rests on this one value.
                throw new IllegalStateException("security.clients[" + client.id()
                        + "].key must be at least 32 characters");
            }
            Set<Scope> scopes = EnumSet.noneOf(Scope.class);
            for (String raw : client.scopes()) {
                Scope.fromValue(raw).ifPresentOrElse(scopes::add,
                        () -> log.warn("client {} declares unknown scope '{}' — ignored",
                                client.id(), raw));
            }
            byKey.put(client.key(), new ResolvedClient(client, scopes));
        }
        log.info("api key authentication {} for {} client(s): {}",
                properties.apiKeyEnabled() ? "ENABLED" : "DISABLED",
                properties.clients().size(),
                properties.clients().stream().map(SecurityProperties.Client::id).toList());
    }

    /**
     * @return the caller, or empty when the key is absent or unrecognised
     */
    public Optional<ResolvedClient> authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        // Constant-time comparison against every configured key. A map lookup
        // would be faster but leaks key material through timing; the client count
        // is single digits, so the linear scan costs nothing.
        ResolvedClient match = null;
        byte[] presented = presentedKey.getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<String, ResolvedClient> entry : byKey.entrySet()) {
            if (MessageDigest.isEqual(presented, entry.getKey().getBytes(StandardCharsets.UTF_8))) {
                match = entry.getValue();
            }
        }
        return Optional.ofNullable(match);
    }

    /**
     * Builds the principal. Tenant comes from the headers, EXCEPT for a client
     * pinned to a fixed tenant — for those the headers are ignored entirely, which
     * removes the confused-deputy risk for that caller.
     */
    public Optional<TenantPrincipal> toPrincipal(ResolvedClient client, Long headerOrgId, Long headerProjectId) {
        Long orgId = client.definition().fixedOrgId() != null
                ? client.definition().fixedOrgId() : headerOrgId;
        Long projectId = client.definition().fixedProjectId() != null
                ? client.definition().fixedProjectId() : headerProjectId;

        if (orgId == null || projectId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TenantPrincipal(
                    new TenantRef(orgId, projectId),
                    client.definition().id(),
                    client.scopes(),
                    null,
                    Actor.ActorType.SERVICE,
                    false));
        } catch (IllegalArgumentException e) {
            // Non-positive ids. A malformed tenant is a 401, not a 500.
            return Optional.empty();
        }
    }

    public boolean enabled() {
        return properties.apiKeyEnabled();
    }

    /**
     * The principal used when authentication is switched off. Development only —
     * production startup fails if {@code api-key-enabled} is false.
     */
    public Optional<TenantPrincipal> anonymousDevPrincipal(Long orgId, Long projectId) {
        if (orgId == null || projectId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TenantPrincipal(new TenantRef(orgId, projectId),
                    "auth-disabled", EnumSet.allOf(Scope.class), null,
                    Actor.ActorType.SERVICE, true));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A configured caller, with its scopes parsed once at startup. */
    public record ResolvedClient(SecurityProperties.Client definition, Set<Scope> scopes) {
    }
}
