package com.aigreentick.services.storage.common.constants;

/**
 * HTTP header names used across the service (company API Standard §1–§2).
 *
 * <p>Names shared with the other services are spelled exactly as in
 * template-service ({@code ApiHeaders} / {@code InternalHeaders}); changing one
 * is a cross-service change.
 */
public final class HeaderNames {

    private HeaderNames() {
    }

    public static final String AUTHORIZATION = "Authorization";

    /**
     * The ONLY request-tracking header. Received or generated, echoed on every
     * response, written to {@code meta.requestId}. {@code X-Trace-Id},
     * {@code X-Correlation-Id} and similar must not be used.
     */
    public static final String REQUEST_ID = "X-Request-Id";

    /** Stops a repeated create request from running twice. Required on uploads. */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    /** Secret of the calling service (service-to-service calls). */
    public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

    /** Name of the calling service; checked against the client the key belongs to. */
    public static final String INTERNAL_CALLER = "X-Internal-Caller";

    /**
     * Tenant scope for the request.
     *
     * <p>Trusted ONLY because the caller was authenticated by API key first. An
     * unauthenticated request never reaches the point where they are read. A
     * caller pinned to a fixed tenant ({@code security.clients[].fixed-org-id})
     * has these ignored entirely. See docs/09-security.md §2.
     */
    public static final String ORG_ID = "X-Org-Id";

    /** @see #ORG_ID */
    public static final String PROJECT_ID = "X-Project-Id";

    /** User performing the action, when a user is acting. Logged, not trusted for authorisation. */
    public static final String USER_ID = "X-User-Id";

    public static final String RATELIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String RATELIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String RATELIMIT_RESET = "X-RateLimit-Reset";
    public static final String RETRY_AFTER = "Retry-After";
}
