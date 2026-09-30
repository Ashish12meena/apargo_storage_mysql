package com.apargo.services.storage.common.context;

import com.apargo.services.storage.common.constants.MdcKeys;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Carries the MDC (trace id, request id, tenant, user, job) from the thread that
 * submits a task to the pool thread that runs it, and cleans up afterwards.
 *
 * <p>Applied to EVERY executor and scheduler in the service. Without it a pool
 * thread logs with no trace id, or — worse — with the ids of whatever request
 * last ran on it.
 *
 * <p>A task handed off by a request is marked {@code worker=true}, so audit
 * events raised on the pool thread keep the request's actor with channel
 * {@code WORKER}.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.clear();
            if (submitted != null && !submitted.isEmpty()) {
                MDC.setContextMap(submitted);
                if (submitted.containsKey(MdcKeys.REQUEST_ID)) {
                    MDC.put(MdcKeys.WORKER, Boolean.TRUE.toString());
                }
            }
            try {
                task.run();
            } finally {
                if (previous == null || previous.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
