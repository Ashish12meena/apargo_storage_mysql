package com.aigreentick.services.storage.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code api.*} — how strictly the company API Standard's headers are applied.
 *
 * @param idempotencyKeyRequired  reject upload/create calls without
 *                                {@code X-Idempotency-Key} (400
 *                                {@code IDEMPOTENCY_KEY_REQUIRED}). The standard
 *                                makes the header required on create APIs;
 *                                switch off only for local testing.
 *
 * <p>Only standard header names are accepted; there are no aliases for the
 * pre-standard {@code X-Api-Key} / {@code Idempotency-Key} (ADR-015).
 */
@ConfigurationProperties(prefix = "api")
public record ApiProperties(
        @DefaultValue("true") boolean idempotencyKeyRequired) {
}
