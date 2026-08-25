package com.aigreentick.services.storage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Published as a CI artifact so consumers can generate clients and run contract tests. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storageServiceOpenApi() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Storage Service API")
                        .version("v1")
                        .description("""
                                Media storage and quota control plane.

                                Tenant scope is derived from the verified bearer token; no endpoint
                                accepts an organisation or project id as a parameter on the
                                tenant-facing surface.

                                Mutating endpoints accept an Idempotency-Key header. Contracts are
                                specified in src/main/resources/docs/04-api-contracts.md, which is
                                the source of truth."""))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
