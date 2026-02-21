package com.aigreentick.services.storage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mediaUploadExecutor")
    public Executor mediaUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("media-upload-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.error("Media upload task rejected — pool exhausted"));
        executor.initialize();
        log.info("Media upload thread pool initialized: core=10, max=30, queue=100");
        return executor;
    }
}