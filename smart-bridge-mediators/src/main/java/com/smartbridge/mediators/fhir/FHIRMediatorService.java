package com.smartbridge.mediators.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.mediators.base.BaseMediatorService;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * FHIR Mediator Service for handling HAPI FHIR interactions.
 * Provides routing, change detection, and notification handling for FHIR resources.
 * Integrates with FHIRClientService and FHIRChangeDetectionService.
 */
@Service
public class FHIRMediatorService extends BaseMediatorService {

    private static final Logger logger = LoggerFactory.getLogger(FHIRMediatorService.class);

    private final FHIRClientService fhirClientService;
    private final FHIRChangeDetectionService changeDetectionService;
    private final ObjectMapper objectMapper;
    
    private final String fhirServerUrl;
    private final String authType;
    private final String username;
    private final String password;
    private final String bearerToken;

    public FHIRMediatorService(
            @Value("${fhir.server.url:http://localhost:8080/fhir}") String fhirServerUrl,
            @Value("${fhir.auth.type:NONE}") String authType,
            @Value("${fhir.auth.username:}") String username,
            @Value("${fhir.auth.password:}") String password,
            @Value("${fhir.auth.token:}") String bearerToken,
            FHIRClientService fhirClientService,
            FHIRChangeDetectionService changeDetectionService) {
        
        super(new MediatorConfig(
            "FHIR-Mediator",
            "1.0.0",
            "Mediator for HAPI FHIR server integration",
            buildConfig(fhirServerUrl, authType)
        ));
        
        this.fhirServerUrl = fhirServerUrl;
        this.authType = authType;
        this.username = username;
        this.password = password;
        this.bearerToken = bearerToken;
        this.fhirClientService = fhirClientService;
        this.changeDetectionService = changeDetectionService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        logger.info("FHIR Mediator Service initialized with serverUrl={}, authType={}", 
            fhirServerUrl, authType);
        
        // Initialize FHIR client configuration
        initialize();
    }

    private void initialize() {
        // Configure FHIR client
        fhirClientService.configure(fhirServerUrl);
        
        // Configure authentication
        if ("BASIC".equalsIgnoreCase(authType) && !username.isEmpty()) {
            fhirClientService.configureBasicAuth(username, password);
            logger.info("FHIR client configured with basic authentication");
        } else if ("BEARER".equalsIgnoreCase(authType) && !bearerToken.isEmpty()) {
            fhirClientService.configureBearerToken(bearerToken);
            logger.info("FHIR client configured with bearer token authentication");
        } else {
            logger.info("FHIR client configured without authentication");
        }
    }

    @Override
    protected Object doProcessRequest(Object request, Map<String, String> headers, String requestId) 
            throws MediatorException {
        
        try {
            // Determine operation type and resource type from headers
            String operation = headers.getOrDefault("X-Operation", "CREATE");
            String resourceType = headers.getOrDefault("X-Resource-Type", "Patient");
            String resourceId = headers.get("X-Resource-Id");
            
            logger.info("Processing FHIR request: operation={}, resourceType={}, requestId={}", 
                operation, resourceType, requestId);
            
            // Process based on resource type and operation
            Object response = processResourceOperation(resourceType, operation, resourceId, request);
            
            logger.debug("FHIR request completed successfully: requestId={}", requestId);
            
            return response;
            
        } catch (MediatorException e) {
            logger.error("Mediator error processing FHIR request: requestId={}, error={}", 
                requestId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error processing FHIR request: requestId={}, error={}", 
                requestId, e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error processing FHIR request: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "PROCESS_REQUEST",
                500
            );
        }
    }

    /**
     * Process resource operation based on type and operation
     */
    private Object processResourceOperation(String resourceType, String operation, 
                                           String resourceId, Object request) throws MediatorException {
        
        switch (resourceType.toUpperCase()) {
            case "PATIENT":
                return processPatientOperation(operation, resourceId, request);
            case "OBSERVATION":
                return processObservationOperation(operation, resourceId, request);
            case "TASK":
                return processTaskOperation(operation, resourceId, request);
            case "MEDICATIONREQUEST":
                return processMedicationRequestOperation(operation, resourceId, request);
            case "SUBSCRIPTION":
                return processSubscriptionOperation(operation, resourceId, request);
            default:
                throw new MediatorException(
                    "Unsupported resource type: " + resourceType,
                    "FHIR_MEDIATOR",
                    "PROCESS_RESOURCE",
                    400
                );
        }
    }

    /**
     * Process Patient resource operations
     */
    private Object processPatientOperation(String operation, String resourceId, Object request) 
            throws MediatorException {
        try {
            switch (operation.toUpperCase()) {
                case "CREATE":
                    Patient patient = convertToResource(request, Patient.class);
                    return fhirClientService.createPatient(patient);
                    
                case "UPDATE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for UPDATE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_PATIENT",
                            400
                        );
                    }
                    Patient updatePatient = convertToResource(request, Patient.class);
                    updatePatient.setId(resourceId);
                    return fhirClientService.updatePatient(updatePatient);
                    
                case "GET":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for GET operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_PATIENT",
                            400
                        );
                    }
                    return fhirClientService.getPatient(resourceId);
                    
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "FHIR_MEDIATOR",
                        "PROCESS_PATIENT",
                        400
                    );
            }
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            throw new MediatorException(
                "Error processing Patient operation: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "PROCESS_PATIENT",
                500
            );
        }
    }

    /**
     * Process Observation resource operations
     */
    private Object processObservationOperation(String operation, String resourceId, Object request) 
            throws MediatorException {
        try {
            switch (operation.toUpperCase()) {
                case "CREATE":
                    Observation observation = convertToResource(request, Observation.class);
                    return fhirClientService.createObservation(observation);
                    
                case "UPDATE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for UPDATE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_OBSERVATION",
                            400
                        );
                    }
                    Observation updateObservation = convertToResource(request, Observation.class);
                    updateObservation.setId(resourceId);
                    return fhirClientService.updateObservation(updateObservation);
                    
                case "GET":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for GET operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_OBSERVATION",
                            400
                        );
                    }
                    return fhirClientService.getObservation(resourceId);
                    
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "FHIR_MEDIATOR",
                        "PROCESS_OBSERVATION",
                        400
                    );
            }
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            throw new MediatorException(
                "Error processing Observation operation: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "PROCESS_OBSERVATION",
                500
            );
        }
    }

    /**
     * Process Task resource operations
     */
    private Object processTaskOperation(String operation, String resourceId, Object request) 
            throws MediatorException {
        try {
            switch (operation.toUpperCase()) {
                case "CREATE":
                    Task task = convertToResource(request, Task.class);
                    return fhirClientService.createTask(task);
                    
                case "UPDATE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for UPDATE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_TASK",
                            400
                        );
                    }
                    Task updateTask = convertToResource(request, Task.class);
                    updateTask.setId(resourceId);
                    return fhirClientService.updateTask(updateTask);
                    
                case "GET":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for GET operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_TASK",
                            400
                        );
                    }
                    return fhirClientService.getTask(resourceId);
                    
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "FHIR_MEDIATOR",
                        "PROCESS_TASK",
                        400
                    );
            }
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            throw new MediatorException(
                "Error processing Task operation: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "PROCESS_TASK",
                500
            );
        }
    }

    /**
     * Process MedicationRequest resource operations
     */
    private Object processMedicationRequestOperation(String operation, String resourceId, Object request) 
            throws MediatorException {
        try {
            switch (operation.toUpperCase()) {
                case "CREATE":
                    MedicationRequest medicationRequest = convertToResource(request, MedicationRequest.class);
                    return fhirClientService.createMedicationRequest(medicationRequest);
                    
                case "UPDATE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for UPDATE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_MEDICATION_REQUEST",
                            400
                        );
                    }
                    MedicationRequest updateMedicationRequest = convertToResource(request, MedicationRequest.class);
                    updateMedicationRequest.setId(resourceId);
                    return fhirClientService.updateMedicationRequest(updateMedicationRequest);
                    
                case "GET":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for GET operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_MEDICATION_REQUEST",
                            400
                        );
                    }
                    return fhirClientService.getMedicationRequest(resourceId);
                    
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "FHIR_MEDIATOR",
                        "PROCESS_MEDICATION_REQUEST",
                        400
                    );
            }
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            throw new MediatorException(
                "Error processing MedicationRequest operation: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "PROCESS_MEDICATION_REQUEST",
                500
            );
        }
    }

    /**
     * Process Subscription resource operations
     */
    private Object processSubscriptionOperation(String operation, String resourceId, Object request) 
            throws MediatorException {
        try {
            switch (operation.toUpperCase()) {
                case "CREATE":
                    // Extract subscription parameters from request
                    @SuppressWarnings("unchecked")
                    Map<String, String> params = (Map<String, String>) request;
                    String resourceType = params.get("resourceType");
                    String criteria = params.get("criteria");
                    String webhookUrl = params.get("webhookUrl");
                    
                    return changeDetectionService.createSubscription(resourceType, criteria, webhookUrl);
                    
                case "ACTIVATE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for ACTIVATE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_SUBSCRIPTION",
                            400
                        );
                    }
                    changeDetectionService.activateSubscription(resourceId);
                    return Map.of("status", "activated", "subscriptionId", resourceId);
                    
                case "DEACTIVATE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for DEACTIVATE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_SUBSCRIPTION",
                            400
                        );
                    }
                    changeDetectionService.deactivateSubscription(resourceId);
                    return Map.of("status", "deactivated", "subscriptionId", resourceId);
                    
                case "DELETE":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for DELETE operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_SUBSCRIPTION",
                            400
                        );
                    }
                    changeDetectionService.deleteSubscription(resourceId);
                    return Map.of("status", "deleted", "subscriptionId", resourceId);
                    
                case "GET":
                    if (resourceId == null || resourceId.isEmpty()) {
                        throw new MediatorException(
                            "Resource ID required for GET operation",
                            "FHIR_MEDIATOR",
                            "PROCESS_SUBSCRIPTION",
                            400
                        );
                    }
                    return fhirClientService.getSubscription(resourceId);
                    
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "FHIR_MEDIATOR",
                        "PROCESS_SUBSCRIPTION",
                        400
                    );
            }
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            throw new MediatorException(
                "Error processing Subscription operation: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "PROCESS_SUBSCRIPTION",
                500
            );
        }
    }

    @Override
    protected HealthCheckResult doHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Test FHIR server connectivity by checking if client is configured
            boolean configured = fhirClientService.isConfigured();
            long duration = System.currentTimeMillis() - startTime;
            
            if (configured) {
                logger.debug("FHIR health check passed: duration={}ms", duration);
                return new HealthCheckResult(true, "FHIR server is reachable", duration);
            } else {
                logger.warn("FHIR health check failed: client not configured");
                return new HealthCheckResult(false, "FHIR client not configured", duration);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("FHIR health check error: {}", e.getMessage(), e);
            return new HealthCheckResult(false, "Health check error: " + e.getMessage(), duration);
        }
    }

    @Override
    protected String doAuthenticate(Map<String, String> credentials) throws MediatorException {
        try {
            logger.info("Authenticating with FHIR server");
            
            String authTypeParam = credentials.getOrDefault("authType", "NONE");
            
            if ("BASIC".equalsIgnoreCase(authTypeParam)) {
                String user = credentials.get("username");
                String pass = credentials.get("password");
                
                if (user == null || pass == null) {
                    throw new MediatorException(
                        "Username and password required for basic authentication",
                        "FHIR_MEDIATOR",
                        "AUTHENTICATE",
                        400
                    );
                }
                
                fhirClientService.configureBasicAuth(user, pass);
                logger.info("Successfully configured basic authentication with FHIR server");
                return "BASIC_AUTH_CONFIGURED";
                
            } else if ("BEARER".equalsIgnoreCase(authTypeParam)) {
                String token = credentials.get("token");
                
                if (token == null) {
                    throw new MediatorException(
                        "Token required for bearer authentication",
                        "FHIR_MEDIATOR",
                        "AUTHENTICATE",
                        400
                    );
                }
                
                fhirClientService.configureBearerToken(token);
                logger.info("Successfully configured bearer token authentication with FHIR server");
                return "BEARER_AUTH_CONFIGURED";
                
            } else {
                logger.info("No authentication configured (NONE)");
                return "NO_AUTH";
            }
            
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Authentication failed with FHIR server: {}", e.getMessage(), e);
            throw new MediatorException(
                "Authentication failed: " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "AUTHENTICATE",
                401
            );
        }
    }

    @Override
    protected void validateRequest(Object request, Map<String, String> headers) throws MediatorException {
        super.validateRequest(request, headers);
        
        // Additional validation for FHIR requests
        String operation = headers.getOrDefault("X-Operation", "CREATE");
        String resourceType = headers.getOrDefault("X-Resource-Type", "Patient");
        
        // Validate resource type
        if (!isValidResourceType(resourceType)) {
            throw new MediatorException(
                "Invalid resource type: " + resourceType,
                "FHIR_MEDIATOR",
                "VALIDATE_REQUEST",
                400
            );
        }
        
        // Validate operation
        if (!isValidOperation(operation)) {
            throw new MediatorException(
                "Invalid operation: " + operation,
                "FHIR_MEDIATOR",
                "VALIDATE_REQUEST",
                400
            );
        }
    }

    @Override
    protected Map<String, String> getEndpoints() {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/fhir/health");
        endpoints.put("patient", "/fhir/Patient");
        endpoints.put("observation", "/fhir/Observation");
        endpoints.put("task", "/fhir/Task");
        endpoints.put("medicationRequest", "/fhir/MedicationRequest");
        endpoints.put("subscription", "/fhir/Subscription");
        endpoints.put("authenticate", "/fhir/auth");
        return endpoints;
    }

    @Override
    protected Map<String, Object> getDefaultChannelConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "FHIR Channel");
        config.put("urlPattern", "^/fhir/.*$");
        config.put("type", "http");
        config.put("allow", new String[]{"admin", "fhir-user"});
        config.put("routes", Map.of(
            "name", "FHIR Mediator Route",
            "host", "localhost",
            "port", 8082,
            "path", "/fhir",
            "primary", true
        ));
        return config;
    }

    /**
     * Convert request object to FHIR resource
     */
    private <T extends Resource> T convertToResource(Object request, Class<T> resourceClass) 
            throws MediatorException {
        try {
            if (resourceClass.isInstance(request)) {
                return resourceClass.cast(request);
            }
            
            // Convert from JSON or Map
            String json = objectMapper.writeValueAsString(request);
            return objectMapper.readValue(json, resourceClass);
            
        } catch (Exception e) {
            logger.error("Error converting request to {}: {}", resourceClass.getSimpleName(), e.getMessage(), e);
            throw new MediatorException(
                "Invalid request format for " + resourceClass.getSimpleName() + ": " + e.getMessage(),
                e,
                "FHIR_MEDIATOR",
                "CONVERT_REQUEST",
                400
            );
        }
    }

    /**
     * Check if resource type is valid
     */
    private boolean isValidResourceType(String resourceType) {
        return resourceType != null && (
            resourceType.equalsIgnoreCase("Patient") ||
            resourceType.equalsIgnoreCase("Observation") ||
            resourceType.equalsIgnoreCase("Task") ||
            resourceType.equalsIgnoreCase("MedicationRequest") ||
            resourceType.equalsIgnoreCase("Subscription")
        );
    }

    /**
     * Check if operation is valid
     */
    private boolean isValidOperation(String operation) {
        return operation != null && (
            operation.equalsIgnoreCase("CREATE") ||
            operation.equalsIgnoreCase("UPDATE") ||
            operation.equalsIgnoreCase("GET") ||
            operation.equalsIgnoreCase("DELETE") ||
            operation.equalsIgnoreCase("ACTIVATE") ||
            operation.equalsIgnoreCase("DEACTIVATE")
        );
    }

    /**
     * Build mediator configuration map
     */
    private static Map<String, Object> buildConfig(String serverUrl, String authType) {
        Map<String, Object> config = new HashMap<>();
        config.put("serverUrl", serverUrl);
        config.put("authType", authType);
        return config;
    }

    /**
     * Get the FHIR client service (for testing purposes)
     */
    protected FHIRClientService getFhirClientService() {
        return fhirClientService;
    }

    /**
     * Get the change detection service (for testing purposes)
     */
    protected FHIRChangeDetectionService getChangeDetectionService() {
        return changeDetectionService;
    }
}
