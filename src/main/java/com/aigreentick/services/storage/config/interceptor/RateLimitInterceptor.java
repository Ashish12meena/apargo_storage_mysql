package com.aigreentick.services.storage.config.interceptor;

import com.aigreentick.services.storage.config.properties.RateLimitConfig;
import com.aigreentick.services.storage.context.UserContext;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig rateLimitConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!rateLimitConfig.isEnabled()) return true;

        String key = resolveKey(request);
        String endpointName = resolveEndpointName(request);

        Bucket bucket = rateLimitConfig.resolveBucket(key, endpointName);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"status\":\"ERROR\",\"message\":\"Rate limit exceeded. Try again in %d seconds\",\"data\":null}",
                waitForRefill));

        log.warn("Rate limit exceeded for key: {} - Retry after: {}s", key, waitForRefill);
        return false;
    }

    private String resolveKey(HttpServletRequest request) {
        Long orgId = UserContext.getOrganisationId();
        Long projectId = UserContext.getProjectId();
        // Rate limit per project within an org
        if (orgId != null && projectId != null) {
            return "org:" + orgId + ":proj:" + projectId;
        }
        return "ip:" + getClientIP(request);
    }

    private String resolveEndpointName(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("/upload")) return "upload";
        if (uri.matches(".*/media/[^/]+$")) return "get-media";
        return "default";
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) return xfHeader.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}