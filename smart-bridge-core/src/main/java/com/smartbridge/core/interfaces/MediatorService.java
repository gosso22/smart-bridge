package com.smartbridge.core.interfaces;

import java.util.Map;

/**
 * Interface for OpenHIM mediator services that handle system-specific interactions.
 * Implementations should provide routing, authentication, and error handling capabilities.
 */
public interface MediatorService {

    /**
     * Process incoming request and route to appropriate transformation endpoint.
     * 
     * @param request The incoming request data
     * @param headers Request headers including authentication
     * @return Response data after processing
     * @throws MediatorException if processing fails
     */
    Object processRequest(Object request, Map<String, String> headers) throws MediatorException;

    /**
     * Register this mediator with OpenHIM core.
     * 
     * @return true if registration successful, false otherwise
     */
    boolean registerWithOpenHIM();

    /**
     * Perform health check for this mediator.
     * 
     * @return Health check status and details
     */
    HealthCheckResult performHealthCheck();

    /**
     * Get mediator configuration and metadata.
     * 
     * @return Mediator configuration object
     */
    MediatorConfig getConfiguration();

    /**
     * Handle authentication for the target system.
     * 
     * @param credentials Authentication credentials
     * @return Authentication token or result
     * @throws MediatorException if authentication fails
     */
    String authenticate(Map<String, String> credentials) throws MediatorException;

    /**
     * Health check result container.
     */
    class HealthCheckResult {
        private final boolean healthy;
        private final String message;
        private final long responseTimeMs;

        public HealthCheckResult(boolean healthy, String message, long responseTimeMs) {
            this.healthy = healthy;
            this.message = message;
            this.responseTimeMs = responseTimeMs;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }

        public long getResponseTimeMs() {
            return responseTimeMs;
        }
    }

    /**
     * Mediator configuration container.
     */
    class MediatorConfig {
        private final String name;
        private final String version;
        private final String description;
        private final Map<String, Object> config;

        public MediatorConfig(String name, String version, String description, Map<String, Object> config) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.config = config;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public Map<String, Object> getConfig() {
            return config;
        }
    }
}