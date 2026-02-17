package com.smartbridge.core.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Interceptor to automatically capture audit context from HTTP requests.
 * Extracts user identification and request metadata for audit logging.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Extract user information from request
        String userId = extractUserId(request);
        String userName = extractUserName(request);
        String sourceIp = extractSourceIp(request);
        String requestId = extractOrGenerateRequestId(request);

        // Set audit context for this request
        AuditContext.setContext(userId, userName, sourceIp, requestId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        // Clear audit context after request completion
        AuditContext.clearContext();
    }

    /**
     * Extract user ID from request headers or authentication context.
     */
    private String extractUserId(HttpServletRequest request) {
        // Try to get from custom header
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }

        // Try to get from authentication header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In a real implementation, decode JWT token to extract user ID
            return "USER_FROM_TOKEN";
        }

        // Try to get from basic auth
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            return "USER_FROM_BASIC_AUTH";
        }

        // Default to anonymous
        return "ANONYMOUS";
    }

    /**
     * Extract user name from request headers.
     */
    private String extractUserName(HttpServletRequest request) {
        String userName = request.getHeader("X-User-Name");
        if (userName != null && !userName.isEmpty()) {
            return userName;
        }
        return "Unknown User";
    }

    /**
     * Extract source IP address from request.
     */
    private String extractSourceIp(HttpServletRequest request) {
        // Check for proxy headers first
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                ip = ip.substring(0, commaIndex);
            }
            return ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // Fall back to remote address
        return request.getRemoteAddr();
    }

    /**
     * Extract or generate request ID for tracing.
     */
    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isEmpty()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
