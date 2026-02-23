package com.aigreentick.services.storage.config.interceptor;

import com.aigreentick.services.storage.constants.HeaderConstants;
import com.aigreentick.services.storage.context.UserContext;
import com.aigreentick.services.storage.context.UserContextData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String orgIdHeader = request.getHeader(HeaderConstants.ORG_ID);
        String projectIdHeader = request.getHeader(HeaderConstants.PROJECT_ID);

        Long orgId = orgIdHeader != null ? Long.valueOf(orgIdHeader) : null;
        Long projectId = projectIdHeader != null ? Long.valueOf(projectIdHeader) : null;

        log.info("{}={} {}={} | UserContext set",
                HeaderConstants.ORG_ID, orgId, HeaderConstants.PROJECT_ID, projectId);

        UserContext.set(new UserContextData(orgId, projectId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}