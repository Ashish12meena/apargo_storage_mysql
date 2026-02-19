
// ── config/StorageProviderConfig.java ────────────────────────────────────────
package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.service.port.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageProviderConfig {

    @Value("${storage.active-provider:local}")
    private String activeProvider;

    private final List<StoragePort> providers;

    @Bean
    @Primary
    public StoragePort storagePort() {
        Map<String, StoragePort> map = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderType().name().toLowerCase(),
                        Function.identity()));

        StoragePort selected = map.get(activeProvider.toLowerCase());
        if (selected == null) {
            throw new IllegalStateException(
                    "Storage provider '" + activeProvider + "' not found. Available: " + map.keySet());
        }

        log.info("Active storage provider: {} ({})",
                selected.getProviderType(), selected.getProviderType().getDisplayName());
        return selected;
    }
}