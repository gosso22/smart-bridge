package com.smartbridge.mediators.base;

import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.MediatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base implementation of OpenHIM mediator service.
 * Provides common functionality for mediator registration, health checks,
 * transaction logging, and audit trail management.
 * 
 * Concrete mediator implementations should extend this class and implement
 * the abstract methods for system-specific logic.
 */
public abstract class BaseMediatorService implements MediatorService {

    private static final Logger logger = LoggerFactory.getLogger(BaseMediatorService.class);

    @Autowired
    protected AuditLogger auditLogger;

    @Autowired(required = false)
    protected OpenHIMClient openHIMClient;

    private final MediatorConfig config;
    private boolean registered = false;

    protected BaseMediatorService(MediatorConfig config) {
        this.config = config;
    }

    @Override
    public Object processRequest(Object request, Map<String, String> headers) throws MediatorException {
        String requestId = generateRequestId();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Processing request {} for mediator {}", requestId, config.getName());
            
            // Log the incoming request
            logTransaction(requestId, "REQUEST_RECEIVED", true, "Request received for processing");
            
            // Validate request
            validateRequest(request, headers);
            
            // Process the request (implemented by subclasses)
            Object response = doProcessRequest(request, headers, requestId);
            
            // Log successful processing
            long duration = System.currentTimeMillis() - startTime;
            logTransaction(requestId, "REQUEST_COMPLETED", true, 
                String.format("Request completed successfully in %dms", duration));
            
            auditLogger.logMediatorOperation(
                config.getName(), 
                "PROCESS_REQUEST", 
                requestId, 
                true, 
                duration, 
                "Request processed successfully"
            );
            
            return response;
            
        } catch (MediatorException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Mediator processing failed for request {}: {}", requestId, e.getMessage(), e);
            
            logTransaction(requestId, "REQUEST_FAILED", false, e.getMessage());
            
            auditLogger.logMediatorOperation(
                config.getName(), 
                "PROCESS_REQUEST", 
                requestId, 
                false, 
                duration, 
                "Request failed: " + e.getMessage()
            );
            
            throw e;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Unexpected error processing request {}: {}", requestId, e.getMessage(), e);
            
            logTransaction(requestId, "REQUEST_ERROR", false, e.getMessage());
            
            auditLogger.logError(
                config.getName(),
                "PROCESS_REQUEST",
                "UNEXPECTED_ERROR",
                e.getMessage(),
                Map.of("requestId", requestId)
            );
            
            throw new MediatorException(
                "Unexpected error processing request: " + e.getMessage(),
                e,
                config.getName(),
                "PROCESS_REQUEST",
                500
            );
        }
    }

    @Override
    public boolean registerWithOpenHIM() {
        if (openHIMClient == null) {
            logger.warn("OpenHIM client not configured, skipping registration for {}", config.getName());
            return false;
        }
        
        try {
            logger.info("Registering mediator {} with OpenHIM", config.getName());
            
            MediatorRegistration registration = buildRegistration();
            boolean success = openHIMClient.registerMediator(registration);
            
            if (success) {
                registered = true;
                logger.info("Successfully registered mediator {} with OpenHIM", config.getName());
                
                auditLogger.logMediatorOperation(
                    config.getName(),
                    "REGISTRATION",
                    UUID.randomUUID().toString(),
                    true,
                    0,
                    "Mediator registered with OpenHIM"
                );
            } else {
                logger.error("Failed to register mediator {} with OpenHIM", config.getName());
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("Error registering mediator {} with OpenHIM: {}", config.getName(), e.getMessage(), e);
            
            auditLogger.logError(
                config.getName(),
                "REGISTRATION",
                "REGISTRATION_FAILED",
                e.getMessage(),
                Map.of("mediator", config.getName())
            );
            
            return false;
        }
    }

    @Override
    public HealthCheckResult performHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("Performing health check for mediator {}", config.getName());
            
            // Perform mediator-specific health checks
            HealthCheckResult result = doHealthCheck();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (!result.isHealthy()) {
                logger.warn("Health check failed for mediator {}: {}", config.getName(), result.getMessage());
                
                auditLogger.logMediatorOperation(
                    config.getName(),
                    "HEALTH_CHECK",
                    UUID.randomUUID().toString(),
                    false,
                    duration,
                    "Health check failed: " + result.getMessage()
                );
            }
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Health check error for mediator {}: {}", config.getName(), e.getMessage(), e);
            
            auditLogger.logError(
                config.getName(),
                "HEALTH_CHECK",
                "HEALTH_CHECK_ERROR",
                e.getMessage(),
                Map.of("mediator", config.getName())
            );
            
            return new HealthCheckResult(false, "Health check error: " + e.getMessage(), duration);
        }
    }

    @Override
    public MediatorConfig getConfiguration() {
        return config;
    }

    @Override
    public String authenticate(Map<String, String> credentials) throws MediatorException {
        try {
            logger.debug("Authenticating for mediator {}", config.getName());
            
            String token = doAuthenticate(credentials);
            
            auditLogger.logSecurityEvent(
                "AUTHENTICATION",
                credentials.getOrDefault("username", "unknown"),
                credentials.getOrDefault("sourceIp", "unknown"),
                true,
                "Authentication successful for mediator " + config.getName()
            );
            
            return token;
            
        } catch (MediatorException e) {
            auditLogger.logSecurityEvent(
                "AUTHENTICATION",
                credentials.getOrDefault("username", "unknown"),
                credentials.getOrDefault("sourceIp", "unknown"),
                false,
                "Authentication failed: " + e.getMessage()
            );
            
            throw e;
        }
    }

    /**
     * Abstract method for subclasses to implement request processing logic.
     */
    protected abstract Object doProcessRequest(Object request, Map<String, String> headers, String requestId) 
        throws MediatorException;

    /**
     * Abstract method for subclasses to implement health check logic.
     */
    protected abstract HealthCheckResult doHealthCheck();

    /**
     * Abstract method for subclasses to implement authentication logic.
     */
    protected abstract String doAuthenticate(Map<String, String> credentials) throws MediatorException;

    /**
     * Validate incoming request. Can be overridden by subclasses.
     */
    protected void validateRequest(Object request, Map<String, String> headers) throws MediatorException {
        if (request == null) {
            throw new MediatorException(
                "Request cannot be null",
                config.getName(),
                "VALIDATE_REQUEST",
                400
            );
        }
    }

    /**
     * Build mediator registration object for OpenHIM.
     */
    protected MediatorRegistration buildRegistration() {
        return new MediatorRegistration(
            config.getName(),
            config.getVersion(),
            config.getDescription(),
            getEndpoints(),
            getDefaultChannelConfig()
        );
    }

    /**
     * Get mediator endpoints. Can be overridden by subclasses.
     */
    protected Map<String, String> getEndpoints() {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/health");
        return endpoints;
    }

    /**
     * Get default channel configuration. Can be overridden by subclasses.
     */
    protected Map<String, Object> getDefaultChannelConfig() {
        return new HashMap<>();
    }

    /**
     * Log transaction for audit trail.
     */
    protected void logTransaction(String requestId, String status, boolean success, String details) {
        TransactionLog transaction = new TransactionLog(
            requestId,
            config.getName(),
            status,
            success,
            Instant.now(),
            details
        );
        
        logger.info("Transaction: {}", transaction);
    }

    /**
     * Generate unique request ID.
     */
    protected String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Check if mediator is registered with OpenHIM.
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Transaction log entry.
     */
    protected static class TransactionLog {
        private final String requestId;
        private final String mediatorName;
        private final String status;
        private final boolean success;
        private final Instant timestamp;
        private final String details;

        public TransactionLog(String requestId, String mediatorName, String status, 
                            boolean success, Instant timestamp, String details) {
            this.requestId = requestId;
            this.mediatorName = mediatorName;
            this.status = status;
            this.success = success;
            this.timestamp = timestamp;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format(
                "TransactionLog{requestId='%s', mediator='%s', status='%s', success=%s, timestamp=%s, details='%s'}",
                requestId, mediatorName, status, success, timestamp, details
            );
        }

        public String getRequestId() { return requestId; }
        public String getMediatorName() { return mediatorName; }
        public String getStatus() { return status; }
        public boolean isSuccess() { return success; }
        public Instant getTimestamp() { return timestamp; }
        public String getDetails() { return details; }
    }
}
