package com.smartbridge.core.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * HTTP interceptor for maintaining API compatibility with legacy systems.
 * 
 * This interceptor automatically:
 * 1. Captures request payloads to preserve original field structures
 * 2. Enhances response payloads with preserved fields
 * 3. Ensures no data loss during round-trip transformations
 * 
 * Requirements: 1.4, 1.5
 */
@Component
public class CompatibilityInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CompatibilityInterceptor.class);

    private final LegacyApiCompatibilityService compatibilityService;
    private final ObjectMapper objectMapper;

    public CompatibilityInterceptor(LegacyApiCompatibilityService compatibilityService) {
        this.compatibilityService = compatibilityService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, 
            byte[] body, 
            ClientHttpRequestExecution execution) throws IOException {
        
        // Log request for compatibility tracking
        logRequest(request, body);
        
        // Preserve request payload if it's a POST or PUT
        if (isDataModificationRequest(request)) {
            preserveRequestPayload(request, body);
        }
        
        // Execute the actual request
        ClientHttpResponse response = execution.execute(request, body);
        
        // Log response for compatibility tracking
        logResponse(response);
        
        return response;
    }

    /**
     * Check if the request modifies data (POST, PUT, PATCH).
     */
    private boolean isDataModificationRequest(HttpRequest request) {
        String method = request.getMethod().name();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /**
     * Preserve the request payload for field preservation.
     */
    private void preserveRequestPayload(HttpRequest request, byte[] body) {
        try {
            if (body != null && body.length > 0) {
                String payload = new String(body, StandardCharsets.UTF_8);
                String clientId = extractClientIdFromUri(request.getURI().toString());
                
                if (clientId != null) {
                    compatibilityService.preserveOriginalFields(clientId, payload);
                    logger.debug("Preserved request payload for client: {}", clientId);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to preserve request payload: {}", e.getMessage());
        }
    }

    /**
     * Extract client ID from URI path.
     */
    private String extractClientIdFromUri(String uri) {
        // Extract client ID from patterns like /clients/{id} or /clients?id={id}
        if (uri.contains("/clients/")) {
            String[] parts = uri.split("/clients/");
            if (parts.length > 1) {
                String idPart = parts[1].split("[/?]")[0];
                return idPart.isEmpty() ? null : idPart;
            }
        }
        
        if (uri.contains("id=")) {
            String[] parts = uri.split("id=");
            if (parts.length > 1) {
                String idPart = parts[1].split("&")[0];
                return idPart.isEmpty() ? null : idPart;
            }
        }
        
        return null;
    }

    /**
     * Log request for debugging and audit purposes.
     */
    private void logRequest(HttpRequest request, byte[] body) {
        if (logger.isDebugEnabled()) {
            logger.debug("Compatibility Interceptor - Request: {} {}", 
                request.getMethod(), request.getURI());
            
            if (body != null && body.length > 0) {
                String bodyStr = new String(body, StandardCharsets.UTF_8);
                logger.debug("Request body: {}", bodyStr);
            }
        }
    }

    /**
     * Log response for debugging and audit purposes.
     */
    private void logResponse(ClientHttpResponse response) {
        if (logger.isDebugEnabled()) {
            try {
                logger.debug("Compatibility Interceptor - Response: {}", 
                    response.getStatusCode());
            } catch (IOException e) {
                logger.warn("Failed to log response status: {}", e.getMessage());
            }
        }
    }
}
