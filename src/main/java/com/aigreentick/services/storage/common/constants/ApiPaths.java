package com.aigreentick.services.storage.common.constants;

/**
 * Every route this service exposes.
 *
 * <p>{@link #MEDIA_SERVE} is FROZEN: absolute URLs built from it are persisted in
 * another service's database, so the path may never move. Behaviour may change;
 * the URL may not.
 */
public final class ApiPaths {

    private ApiPaths() {
    }

    public static final String API_V1 = "/api/v1";
    public static final String MEDIA = API_V1 + "/media";
    public static final String QUOTA = API_V1 + "/quota";

    /** FROZEN — persisted downstream. */
    public static final String MEDIA_SERVE = MEDIA + "/serve";

    /**
     * Batch upload. The multipart field name is {@code files}, hardcoded by
     * {@code template-service}; this path must stay a prefix-match sibling of
     * {@code /media/upload} so the rate limiter can single it out.
     */
    public static final String MEDIA_UPLOAD_BATCH = MEDIA + "/upload/batch";

    /** Direct-upload sessions; {@code Location} of a created session. */
    public static final String MEDIA_UPLOADS = MEDIA + "/uploads";

    /** {@code Location} value for a media item: a path, so no internal host leaks through a gateway. */
    public static String mediaLocation(String mediaId) {
        return MEDIA + "/" + mediaId;
    }

    /** {@code Location} value for a direct-upload session. */
    public static String uploadSessionLocation(String uploadId) {
        return MEDIA_UPLOADS + "/" + uploadId;
    }

    /** Servlet error path, answered in the standard wrapper by {@code ApiErrorController}. */
    public static final String ERROR = "/error";

    public static final String INTERNAL = "/internal";
    public static final String INTERNAL_QUOTA = INTERNAL + "/quota";
    public static final String INTERNAL_MEDIA = INTERNAL + "/media";
}
