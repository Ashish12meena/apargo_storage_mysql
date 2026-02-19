package com.aigreentick.services.storage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger documentation configuration.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${info.app.version}")
    private String version;

    @Value("${info.app.description}")
    private String description;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Storage Service API")
                        .version(version)
                        .description(description)
                        .contact(new Contact()
                                .name("Ashish Meena")
                                .email("ashish@aigreentick.com")
                                .url("https://aigreentick.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:7998").description("Local Development"),
                        new Server().url("https://api.aigreentick.com").description("Production")
                ))
                .tags(List.of(
                        new Tag().name("Media Management").description("Operations for managing media files"),
                        new Tag().name("Media Upload").description("Operations for uploading and retrieving media"),
                        new Tag().name("Health & Monitoring").description("Health check and monitoring endpoints")
                ));
    }
}