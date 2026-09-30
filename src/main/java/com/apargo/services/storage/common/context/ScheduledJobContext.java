package com.apargo.services.storage.common.context;

import com.apargo.services.storage.common.constants.MdcKeys;
import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs one scheduled job execution in its own context: a NEW trace id, the job
 * name in the MDC, and nothing left over from whatever last used the thread.
 *
 * <p>Every {@code @Scheduled} method goes through here. The job name then shows
 * on every log line of the run and becomes the audit actor
 * ({@code SYSTEM/<jobName>}, channel {@code WORKER}).
 */
public final class ScheduledJobContext {

    private ScheduledJobContext() {
    }

    public static void run(String jobName, Runnable job) {
        call(jobName, () -> {
            job.run();
            return null;
        });
    }

    public static <T> T call(String jobName, Supplier<T> job) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName is required");
        }
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.clear();
        MDC.put(MdcKeys.JOB_NAME, jobName);
        MDC.put(MdcKeys.TRACE_ID, TraceContext.newTraceId());
        try {
            return job.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(Map<String, String> previous) {
        if (previous == null || previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
