package com.aigreentick.services.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Storage Service — the control plane for uploaded files and storage quota.
 *
 * <p>Deliberately carries no {@code @Enable*} annotations. Configuration lives
 * beside the thing it configures, in {@code config}, so it is discoverable from
 * the code it affects rather than three packages away:
 *
 * <ul>
 *   <li>{@code PropertiesConfig} — registers the typed property records</li>
 *   <li>{@code SchedulingConfig} — enables scheduling and sizes its thread pool</li>
 * </ul>
 *
 * <p>{@code @EnableTransactionManagement} is absent on purpose: Boot's
 * {@code TransactionAutoConfiguration} already applies it whenever a
 * {@code PlatformTransactionManager} exists, which the JPA starter guarantees.
 * A no-op annotation is worse than none — the next reader cannot tell whether it
 * is load-bearing.
 *
 * <p>Documentation is the source of truth: {@code src/main/resources/docs}.
 */
@SpringBootApplication
public class StorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageApplication.class, args);
    }
}
