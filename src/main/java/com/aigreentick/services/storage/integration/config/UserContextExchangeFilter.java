package com.aigreentick.services.storage.integration.config;

import com.aigreentick.services.storage.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserContextExchangeFilter implements RequestInterceptor {

    private static final String ORG_ID_HEADER = "X-Org-Id";
    private static final String PROJECT_ID_HEADER = "X-Project-Id";

    @Override
    public void apply(RequestTemplate template) {
        Long orgId = UserContext.getOrganisationId();
        Long projectId = UserContext.getProjectId();

        if (orgId != null) {
            template.header(ORG_ID_HEADER, String.valueOf(orgId));
        }
        if (projectId != null) {
            template.header(PROJECT_ID_HEADER, String.valueOf(projectId));
        }
    }
}