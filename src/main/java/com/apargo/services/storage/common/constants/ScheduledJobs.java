package com.apargo.services.storage.common.constants;

/**
 * Names of the scheduled jobs. One name per job, used for three things that must
 * agree: the distributed lock, the {@code jobName} MDC key on every log line of a
 * run, and the audit actor ({@code SYSTEM/<job>}) of every change the run makes.
 */
public final class ScheduledJobs {

    private ScheduledJobs() {
    }

    /** Outbox polling; also runs the outbox handlers (reaper, scan, teardown). */
    public static final String OUTBOX_DISPATCH = "outbox-dispatch";

    public static final String SWEEP_SESSIONS = "sweep-sessions";
    public static final String PURGE_MEDIA = "purge-media";
    public static final String RECONCILE_QUOTA = "reconcile-quota";
    public static final String RECLAIM_ORPHANS = "reclaim-orphans";
    public static final String PURGE_RECORDS = "purge-records";
}
