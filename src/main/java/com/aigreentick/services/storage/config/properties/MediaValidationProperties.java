package com.aigreentick.services.storage.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * {@code media.validation.*} — the SINGLE source of upload limits and allowlists.
 *
 * <p>Limits are GLOBAL. Every organisation and project gets the same per-file
 * ceiling and the same MIME allowlist; changing them is a YAML edit and a
 * restart (ADR-012).
 *
 * <p>What DOES vary per tenant is total storage capacity, and that already has a
 * home: {@code project_storage.max_bytes} in the database, set at runtime through
 * the provisioning API. Per-file limits are deployment configuration; total
 * capacity is tenant data. Keeping them in separate stores is deliberate — it is
 * what stops tenant onboarding from requiring a deploy.
 */
@Validated
@ConfigurationProperties(prefix = "media.validation")
public record MediaValidationProperties(
        /** Ceiling for any single file. Per-type entries may lower it, never raise it. */
        @DefaultValue("16777216") @Min(1) long maxBytes,
        @DefaultValue("8192") @Min(512) int inspectionHeaderBytes,
        /** Never false in production; enforced by a startup assertion. */
        @DefaultValue("true") boolean rejectOnTypeMismatch,
        /** Optional per-type override. Absent types fall back to {@link #maxBytes()}. */
        Map<String, Long> maxBytesByMediaType,
        Map<String, List<String>> allowedMimeTypesByMediaType) {

    public MediaValidationProperties {
        maxBytesByMediaType = maxBytesByMediaType == null
                ? Map.of() : Map.copyOf(maxBytesByMediaType);
        allowedMimeTypesByMediaType = allowedMimeTypesByMediaType == null
                ? Map.of() : Map.copyOf(allowedMimeTypesByMediaType);
    }

    public long limitFor(String mediaType) {
        return Math.min(maxBytes, maxBytesByMediaType.getOrDefault(mediaType, maxBytes));
    }
}
