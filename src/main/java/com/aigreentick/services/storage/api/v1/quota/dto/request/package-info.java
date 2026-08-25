/**
 * Intentionally empty.
 *
 * <p>The public quota surface is READ-ONLY: {@code GET /api/v1/quota} returns the
 * calling tenant's own usage and takes no body. Every quota WRITE is an
 * administrative act performed on a tenant from outside, so its request DTO lives
 * under {@code api.internal.quota.dto.request} instead.
 *
 * <p>Kept as a placeholder so the module layout stays symmetric with
 * {@code api.v1.media}, and so nobody adds a public quota-write DTO here without
 * first noticing that the write path is deliberately internal.
 */
package com.aigreentick.services.storage.api.v1.quota.dto.request;
