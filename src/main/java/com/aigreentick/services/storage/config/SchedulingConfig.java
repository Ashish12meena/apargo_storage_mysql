package com.aigreentick.services.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import lombok.extern.slf4j.Slf4j;

/**
 * Enables scheduling and sizes the pool it runs in.
 *
 * <p>{@code @EnableScheduling} is NOT applied by Boot automatically. Without it
 * every {@code @Scheduled} method is silently ignored — no error, no warning — and
 * the outbox simply stops dispatching while nothing gets swept or purged. It lives
 * here rather than on the application class so that the fact scheduling is on, and
 * the pool it uses, are visible from the same file.
 *
 * <p>The pool size matters. Spring's default scheduler is SINGLE-THREADED, and
 * this service runs a 1-second outbox poll alongside jobs that take minutes. On one
 * thread, a slow nightly reconciliation pass blocks outbox dispatch entirely, so
 * deletes stop being reaped for as long as reconciliation runs. Separate threads
 * remove that coupling.
 *
 * <p>Can be switched off wholesale with {@code scheduling.enabled=false} — useful
 * in tests, and for running a web-only replica set with maintenance handled by a
 * dedicated deployment.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler(
            @org.springframework.beans.factory.annotation.Value("${scheduling.pool-size:4}")
            int poolSize) {

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("storage-sched-");

        // A scheduled task that throws would otherwise kill its future silently and
        // never run again. Logging and swallowing keeps the schedule alive; the
        // jobs themselves already handle their own failures.
        scheduler.setErrorHandler(t ->
                log.error("scheduled task failed; schedule continues", t));

        // Let in-flight maintenance finish on shutdown rather than being severed
        // mid-batch — a half-applied sweep is recoverable, but noisy.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);

        log.info("scheduler enabled with pool size {}", poolSize);
        return scheduler;
    }
}
