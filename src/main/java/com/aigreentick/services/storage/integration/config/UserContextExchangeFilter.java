package com.aigreentick.services.storage.integration.config;

import com.aigreentick.services.storage.constants.HeaderConstants;
import com.aigreentick.services.storage.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserContextExchangeFilter implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Long orgId = UserContext.getOrganisationId();
        Long projectId = UserContext.getProjectId();

        if (orgId != null) {
            template.header(HeaderConstants.ORG_ID, String.valueOf(orgId));
        }
        if (projectId != null) {
            template.header(HeaderConstants.PROJECT_ID, String.valueOf(projectId));
        }
    }
}