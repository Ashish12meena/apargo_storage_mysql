package com.aigreentick.services.storage.config;

import com.aigreentick.services.storage.api.security.RequiresIdempotencyKey;
import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.config.properties.ApiProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Published as a CI artifact so consumers can generate clients and run contract tests. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "internalApiKey";

    @Bean
    public OpenAPI storageServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Storage Service API")
                        .version("v1")
                        .description("""
                                Media storage and quota control plane. Follows the company API Standard:
                                every JSON response is {success, status, code, message, data, errors, meta};
                                `status` always equals the HTTP status; lists are data = {items, pagination}
                                (cursor paging: size, cursor → nextCursor, hasNext).

                                Callers authenticate with X-Internal-Api-Key (+ X-Internal-Caller) and
                                name the tenant with X-Org-Id / X-Project-Id. X-Request-Id is the only
                                tracking header. Create operations require X-Idempotency-Key.

                                Contracts: src/main/resources/docs/04-api-contracts.md (source of truth)."""))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(HeaderNames.INTERNAL_API_KEY)));
    }

    /** Headers read by filters/interceptors rather than bound as controller parameters. */
    @Bean
    public OperationCustomizer standardHeadersCustomizer(ApiProperties api) {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(header(HeaderNames.ORG_ID, true, "Organization the request acts for"));
            operation.addParametersItem(header(HeaderNames.PROJECT_ID, true, "Project the request acts for"));
            operation.addParametersItem(header(HeaderNames.INTERNAL_CALLER, false, "Name of the calling service"));
            operation.addParametersItem(header(HeaderNames.USER_ID, false, "User performing the action"));
            operation.addParametersItem(header(HeaderNames.REQUEST_ID, false,
                    "Tracks one request across services; generated and echoed if absent"));
            if (handlerMethod.hasMethodAnnotation(RequiresIdempotencyKey.class)) {
                if (operation.getParameters() != null) {
                    operation.getParameters().removeIf(p -> HeaderNames.IDEMPOTENCY_KEY.equals(p.getName()));
                }
                operation.addParametersItem(header(HeaderNames.IDEMPOTENCY_KEY, api.idempotencyKeyRequired(),
                        "Unique per logical create (a UUID). A repeat returns the first result."));
            }
            return operation;
        };
    }

    private static HeaderParameter header(String name, boolean required, String description) {
        HeaderParameter parameter = new HeaderParameter();
        parameter.setName(name);
        parameter.setRequired(required);
        parameter.setDescription(description);
        parameter.setSchema(new StringSchema());
        return parameter;
    }
}
