package com.aigreentick.services.storage.config.filter;


import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aigreentick.services.storage.config.properties.MediaProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Filter that runs before controllers to check if the service is enabled.
 * Applies only to /api/** endpoints.
 */
@Component
@Order(1) // Ensure this runs early in the filter chain
@RequiredArgsConstructor
public class ServiceEnabledFilter extends OncePerRequestFilter {
    private final MediaProperties mediaProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

         // Apply only to /api/** endpoints
        if (path.startsWith("/api/media/")) {
            // Call the helper method
            if (!isServiceIncomingEnabled(response)) {
                return; // Stop filter chain if service is disabled
            }
        }

         // Apply only to /api/** endpoints
        if (path.startsWith("/api/image/")) {
            // Call the helper method
            if (!isServiceIncomingEnabled(response)) {
                return; // Stop filter chain if service is disabled
            }
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Checks if service is enabled.
     * Returns true if enabled; false if disabled (and writes 503 response)
     */
    private boolean isServiceIncomingEnabled(HttpServletResponse response) throws IOException {
        if (!mediaProperties.isIncomingEnabled()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            String body = """
                    {
                        "status": "ERROR",
                        "message": "Incoming requests are temporarily disabled",
                        "data": null
                    }
                    """;
            response.getWriter().write(body);
            return false; // Stop request processing
        }
        return true; // Service is enabled
    }

}
