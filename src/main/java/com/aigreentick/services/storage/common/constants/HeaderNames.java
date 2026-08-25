package com.aigreentick.services.storage.common.constants;

/** HTTP header names used across the service. */
public final class HeaderNames {

    private HeaderNames() {
    }

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String REQUEST_ID = "X-Request-Id";

    /** API key identifying the calling service. Default header name; configurable. */
    public static final String API_KEY = "X-Api-Key";

    /**
     * Tenant scope for the request.
     *
     * <p>These are trusted ONLY because the caller was authenticated by API key
     * first. An unauthenticated request never reaches the point where they are
     * read. A caller pinned to a fixed tenant
     * ({@code security.clients[].fixed-org-id}) has these ignored entirely.
     *
     * <p>The limitation this leaves open: an authenticated caller may assert a
     * tenant that is not theirs. See docs/09-security.md §2.
     */
    public static final String ORG_ID = "X-Org-Id";

    /** @see #ORG_ID */
    public static final String PROJECT_ID = "X-Project-Id";

    public static final String RATELIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String RATELIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String RATELIMIT_RESET = "X-RateLimit-Reset";
    public static final String RETRY_AFTER = "Retry-After";
    public static final String DEPRECATION = "Deprecation";
    public static final String SUNSET = "Sunset";
    public static final String WARNING = "Warning";
}