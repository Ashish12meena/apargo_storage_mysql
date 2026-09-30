package com.apargo.services.storage.common.constants;

/**
 * MDC keys used across the service. The names appear in every log line and in
 * the structured (JSON) log fields, so they are part of the operational contract:
 * dashboards and alerts query them.
 *
 * <p>Ownership: {@code requestId}, {@code userId}, {@code orgId}, {@code projectId},
 * {@code caller} and {@code internalCaller} are written by {@code RequestIdFilter};
 * {@code traceId} by {@code TraceContextFilter} or {@code ScheduledJobContext};
 * {@code jobName} by {@code ScheduledJobContext}; {@code worker} by
 * {@code MdcTaskDecorator}.
 */
public final class MdcKeys {

    private MdcKeys() {
    }

    /** {@code X-Request-Id}; absent for scheduled jobs. */
    public static final String REQUEST_ID = "requestId";

    /** Backend W3C trace id (32 lower-case hex); present on every request and job. */
    public static final String TRACE_ID = "traceId";

    public static final String ORG_ID = "orgId";
    public static final String PROJECT_ID = "projectId";

    /** {@code X-User-Id} when a user is acting. */
    public static final String USER_ID = "userId";

    /** The authenticated client (e.g. {@code template-service}). */
    public static final String CALLER = "caller";

    /** The calling service on {@code /internal/**} only; drives the audit actor there. */
    public static final String INTERNAL_CALLER = "internalCaller";

    /** Name of the scheduled job running on this thread. */
    public static final String JOB_NAME = "jobName";

    /** {@code "true"} on a pool thread running work handed off by a request. */
    public static final String WORKER = "worker";

    /** Batch correlation for {@code POST /media/upload/batch}. */
    public static final String BATCH_ID = "batchId";
}
