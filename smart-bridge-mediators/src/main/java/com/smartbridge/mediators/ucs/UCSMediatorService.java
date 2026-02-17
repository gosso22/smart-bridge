package com.smartbridge.mediators.ucs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.validation.UCSClientValidator;
import com.smartbridge.mediators.base.BaseMediatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * UCS Mediator Service for handling UCS system interactions.
 * Provides routing, authentication, and error handling for UCS API operations.
 * Supports both basic and token-based authentication methods.
 */
@Service
public class UCSMediatorService extends BaseMediatorService {

    private static final Logger logger = LoggerFactory.getLogger(UCSMediatorService.class);

    private final UCSApiClient ucsApiClient;
    private final UCSClientValidator validator;
    private final ObjectMapper objectMapper;

    @Autowired
    public UCSMediatorService(
            @Value("${ucs.api.baseUrl:http://localhost:8080/ucs}") String ucsBaseUrl,
            @Value("${ucs.auth.type:BASIC}") String authType,
            @Value("${ucs.auth.username:}") String username,
            @Value("${ucs.auth.password:}") String password,
            @Value("${ucs.auth.token:}") String token,
            UCSClientValidator validator) {
        
        super(new MediatorConfig(
            "UCS-Mediator",
            "1.0.0",
            "Mediator for UCS legacy system integration",
            buildConfig(ucsBaseUrl, authType)
        ));
        
        this.validator = validator;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        // Initialize UCS API client with appropriate authentication
        UCSApiClient.UCSAuthConfig authConfig;
        if ("TOKEN".equalsIgnoreCase(authType) && !token.isEmpty()) {
            authConfig = new UCSApiClient.UCSAuthConfig(token);
        } else {
            authConfig = new UCSApiClient.UCSAuthConfig(
                UCSApiClient.AuthType.valueOf(authType.toUpperCase()),
                username,
                password
            );
        }
        
        this.ucsApiClient = new UCSApiClient(ucsBaseUrl, authConfig);
        
        logger.info("UCS Mediator Service initialized with baseUrl={}, authType={}", 
            ucsBaseUrl, authType);
    }

    @Override
    protected Object doProcessRequest(Object request, Map<String, String> headers, String requestId) 
            throws MediatorException {
        
        try {
            // Determine operation type from headers
            String operation = headers.getOrDefault("X-Operation", "CREATE");
            String clientId = headers.get("X-Client-Id");
            
            logger.info("Processing UCS request: operation={}, requestId={}", operation, requestId);
            
            // Log request
            logger.debug("Request payload: {}", objectMapper.writeValueAsString(request));
            
            // Convert request to UCSClient
            UCSClient ucsClient = convertToUCSClient(request);
            
            // Validate UCS client data
            validator.validate(ucsClient);
            
            // Process based on operation type
            UCSClient response;
            switch (operation.toUpperCase()) {
                case "CREATE":
                    response = ucsApiClient.createClient(ucsClient);
                    logger.info("Created client in UCS: requestId={}", requestId);
                    break;
                    
                case "UPDATE":
                    if (clientId == null || clientId.isEmpty()) {
                        throw new MediatorException(
                            "Client ID required for UPDATE operation",
                            "UCS_MEDIATOR",
                            "PROCESS_REQUEST",
                            400
                        );
                    }
                    response = ucsApiClient.updateClient(clientId, ucsClient);
                    logger.info("Updated client in UCS: clientId={}, requestId={}", clientId, requestId);
                    break;
                    
                case "GET":
                    if (clientId == null || clientId.isEmpty()) {
                        throw new MediatorException(
                            "Client ID required for GET operation",
                            "UCS_MEDIATOR",
                            "PROCESS_REQUEST",
                            400
                        );
                    }
                    response = ucsApiClient.getClient(clientId);
                    logger.info("Retrieved client from UCS: clientId={}, requestId={}", clientId, requestId);
                    break;
                    
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "UCS_MEDIATOR",
                        "PROCESS_REQUEST",
                        400
                    );
            }
            
            // Log response
            logger.debug("Response payload: {}", objectMapper.writeValueAsString(response));
            
            return response;
            
        } catch (MediatorException e) {
            logger.error("Mediator error processing UCS request: requestId={}, error={}", 
                requestId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error processing UCS request: requestId={}, error={}", 
                requestId, e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error processing UCS request: " + e.getMessage(),
                e,
                "UCS_MEDIATOR",
                "PROCESS_REQUEST",
                500
            );
        }
    }

    @Override
    protected HealthCheckResult doHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Test UCS API connectivity
            boolean connected = ucsApiClient.testConnection();
            long duration = System.currentTimeMillis() - startTime;
            
            if (connected) {
                logger.debug("UCS health check passed: duration={}ms", duration);
                return new HealthCheckResult(true, "UCS system is reachable", duration);
            } else {
                logger.warn("UCS health check failed: system not reachable");
                return new HealthCheckResult(false, "UCS system is not reachable", duration);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("UCS health check error: {}", e.getMessage(), e);
            return new HealthCheckResult(false, "Health check error: " + e.getMessage(), duration);
        }
    }

    @Override
    protected String doAuthenticate(Map<String, String> credentials) throws MediatorException {
        try {
            logger.info("Authenticating with UCS system");
            
            String token = ucsApiClient.authenticate();
            
            if (token != null) {
                logger.info("Successfully authenticated with UCS system");
                return token;
            } else {
                logger.info("Using basic authentication (no token required)");
                return "BASIC_AUTH";
            }
            
        } catch (Exception e) {
            logger.error("Authentication failed with UCS system: {}", e.getMessage(), e);
            throw new MediatorException(
                "Authentication failed: " + e.getMessage(),
                e,
                "UCS_MEDIATOR",
                "AUTHENTICATE",
                401
            );
        }
    }

    @Override
    protected void validateRequest(Object request, Map<String, String> headers) throws MediatorException {
        super.validateRequest(request, headers);
        
        // Additional validation for UCS requests
        if (request == null) {
            throw new MediatorException(
                "Request body cannot be null",
                "UCS_MEDIATOR",
                "VALIDATE_REQUEST",
                400
            );
        }
        
        String operation = headers.getOrDefault("X-Operation", "CREATE");
        if (!"CREATE".equalsIgnoreCase(operation) && 
            !"UPDATE".equalsIgnoreCase(operation) && 
            !"GET".equalsIgnoreCase(operation)) {
            throw new MediatorException(
                "Invalid operation: " + operation,
                "UCS_MEDIATOR",
                "VALIDATE_REQUEST",
                400
            );
        }
    }

    @Override
    protected Map<String, String> getEndpoints() {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/ucs/health");
        endpoints.put("clients", "/ucs/clients");
        endpoints.put("authenticate", "/ucs/auth");
        return endpoints;
    }

    @Override
    protected Map<String, Object> getDefaultChannelConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "UCS Channel");
        config.put("urlPattern", "^/ucs/.*$");
        config.put("type", "http");
        config.put("allow", new String[]{"admin", "ucs-user"});
        config.put("routes", Map.of(
            "name", "UCS Mediator Route",
            "host", "localhost",
            "port", 8081,
            "path", "/ucs",
            "primary", true
        ));
        return config;
    }

    /**
     * Convert request object to UCSClient.
     */
    private UCSClient convertToUCSClient(Object request) throws MediatorException {
        try {
            if (request instanceof UCSClient) {
                return (UCSClient) request;
            }
            
            // Convert from JSON or Map
            String json = objectMapper.writeValueAsString(request);
            return objectMapper.readValue(json, UCSClient.class);
            
        } catch (Exception e) {
            logger.error("Error converting request to UCSClient: {}", e.getMessage(), e);
            throw new MediatorException(
                "Invalid request format: " + e.getMessage(),
                e,
                "UCS_MEDIATOR",
                "CONVERT_REQUEST",
                400
            );
        }
    }

    /**
     * Build mediator configuration map.
     */
    private static Map<String, Object> buildConfig(String baseUrl, String authType) {
        Map<String, Object> config = new HashMap<>();
        config.put("baseUrl", baseUrl);
        config.put("authType", authType);
        return config;
    }

    /**
     * Get the UCS API client (for testing purposes).
     */
    protected UCSApiClient getUcsApiClient() {
        return ucsApiClient;
    }
}
