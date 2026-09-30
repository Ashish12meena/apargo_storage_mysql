package com.apargo.services.storage.config;

import com.apargo.services.storage.common.context.MdcTaskDecorator;
import com.apargo.services.storage.config.properties.AuditProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * The thread pool audit events are sent to Kafka on. No request thread ever
 * waits for Kafka: the after-commit listener only hands the event to this pool.
 *
 * <p>Deliberately NO caller-runs policy. When the queue is full the pool rejects
 * the event ({@link ThreadPoolExecutor.AbortPolicy}); the publisher logs it at
 * ERROR with the full event JSON and drops it, rather than slowing the request
 * down by sending on the request thread.
 */
@Configuration
@Slf4j
public class AuditPublisherConfig {

    /** Bean name; the publisher injects the pool by this name. */
    public static final String AUDIT_PUBLISHER_EXECUTOR = "auditPublisherExecutor";

    /**
     * @param kafkaOperations not used here. Declared so Spring destroys this pool —
     *                        draining the events still queued — BEFORE the Kafka
     *                        producer it sends through is closed.
     */
    @Bean(name = AUDIT_PUBLISHER_EXECUTOR)
    public ThreadPoolTaskExecutor auditPublisherExecutor(AuditProperties properties,
                                                         KafkaOperations<?, ?> kafkaOperations) {
        AuditProperties.Publisher publisher = properties.publisher();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(publisher.poolSize());
        executor.setMaxPoolSize(publisher.poolSize());
        executor.setQueueCapacity(publisher.queueCapacity());
        executor.setThreadNamePrefix(publisher.threadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(publisher.awaitTerminationSeconds());
        executor.setAllowCoreThreadTimeOut(false);

        log.info("audit publishing {}: topic={} source={} environment={} pool={} queue={}",
                properties.enabled() ? "enabled" : "DISABLED", properties.topics().audit(),
                properties.sourceService(), properties.environment(),
                publisher.poolSize(), publisher.queueCapacity());
        return executor;
    }
}
