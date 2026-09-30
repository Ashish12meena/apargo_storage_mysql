package com.apargo.services.storage.application.audit;

/**
 * Every name that appears in this service's audit events. Business code and the
 * factory use these constants; no audit string literal lives anywhere else.
 *
 * <p>These values are read by the audit service and its dashboards: renaming one
 * is a breaking change for consumers. Adding one is safe.
 */
public final class AuditConstants {

    private AuditConstants() {
    }

    /** Value of {@code module} on every event this service produces. */
    public static final String MODULE = "STORAGE";

    /** Values of {@code entity.type}. */
    public static final class EntityTypes {

        public static final String MEDIA = "MEDIA";
        public static final String UPLOAD_SESSION = "UPLOAD_SESSION";
        public static final String ORG_QUOTA = "ORG_QUOTA";
        public static final String PROJECT_QUOTA = "PROJECT_QUOTA";
        /** A tenant teardown job; the entity id is the job id returned by the API. */
        public static final String STORAGE_TEARDOWN = "STORAGE_TEARDOWN";

        private EntityTypes() {
        }
    }

    /** Values of {@code changes[].field}. */
    public static final class Fields {

        public static final String STATUS = "status";
        public static final String SCAN_STATUS = "scanStatus";
        public static final String MAX_BYTES = "maxBytes";
        public static final String USED_BYTES = "usedBytes";

        private Fields() {
        }
    }

    /** Keys of {@code metadata}. Values are ids, enum names, counts and flags only. */
    public static final class Metadata {

        public static final String MEDIA_ID = "mediaId";
        public static final String MEDIA_TYPE = "mediaType";
        public static final String CONTENT_TYPE = "contentType";
        public static final String SIZE_BYTES = "sizeBytes";
        public static final String DECLARED_SIZE_BYTES = "declaredSizeBytes";
        public static final String UPLOAD_MODE = "uploadMode";
        public static final String UPLOAD_SESSION_ID = "uploadSessionId";
        public static final String RECLAIMED_BYTES = "reclaimedBytes";
        public static final String QUOTA_RELEASED = "quotaReleased";
        public static final String PERMANENT = "permanent";
        public static final String PREVIOUS_STATUS = "previousStatus";
        public static final String SCOPE = "scope";
        public static final String MAX_BYTES = "maxBytes";
        public static final String ESTIMATED_FILES = "estimatedFiles";
        public static final String FILES_REMOVED = "filesRemoved";
        public static final String BATCHES = "batches";
        public static final String ATTEMPTS = "attempts";
        public static final String DRIFT_BYTES = "driftBytes";
        public static final String RECLAIMED_COUNT = "reclaimedCount";
        public static final String STORAGE_PROVIDER = "storageProvider";

        private Metadata() {
        }
    }

    /**
     * {@code error.message} for categories whose real message must not leave the
     * service (SYSTEM, EXTERNAL_SERVICE, AUTHENTICATION, AUTHORIZATION). The
     * {@code error.reference} (trace id) leads to the detail in the logs.
     */
    public static final class Messages {

        public static final String SYSTEM_FAILURE = "An unexpected error occurred.";
        public static final String EXTERNAL_SERVICE_FAILURE = "A required dependency was unavailable.";
        public static final String AUTHENTICATION_FAILURE = "The caller was not authenticated.";
        public static final String AUTHORIZATION_FAILURE = "The caller was not allowed to perform this action.";

        private Messages() {
        }
    }
}
