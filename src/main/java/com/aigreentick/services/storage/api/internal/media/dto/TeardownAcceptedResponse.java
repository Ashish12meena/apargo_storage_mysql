package com.aigreentick.services.storage.api.internal.media.dto;

/**
 * {@code data} of a teardown {@code 202 Accepted} (API Standard §4:
 * {@code {jobId, statusUrl}}).
 *
 * @param jobId     the teardown handle; also recorded in {@code media_audit} and
 *                  on the {@code tenant.teardown.completed} event
 * @param statusUrl the internal quota resource of the target; usage falls to
 *                  zero as the teardown progresses (there is no job resource)
 * @param scope     {@code PROJECT} or {@code ORG}
 * @param permanent whether the grace period is skipped
 */
public record TeardownAcceptedResponse(String jobId, String statusUrl, String scope, boolean permanent) {
}
