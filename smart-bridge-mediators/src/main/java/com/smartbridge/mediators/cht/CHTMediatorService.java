package com.smartbridge.mediators.cht;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.mediators.base.BaseMediatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenHIM Mediator Service for CHT system interactions.
 * Routes CREATE/UPDATE/GET operations via CHTApiClient based on X-Operation header.
 * Supports Basic Auth (CHT standard).
 */
@Service
public class CHTMediatorService extends BaseMediatorService {

    private static final Logger logger = LoggerFactory.getLogger(CHTMediatorService.class);

    private final CHTApiClient chtApiClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public CHTMediatorService(
            @Value("${smartbridge.cht.api-url:http://localhost:5988}") String chtBaseUrl,
            @Value("${smartbridge.cht.username:medic}") String username,
            @Value("${smartbridge.cht.password:password}") String password,
            @Value("${smartbridge.cht.couchdb-url:}") String couchdbUrl) {

        super(new MediatorConfig(
            "CHT-Mediator",
            "1.0.0",
            "Mediator for Community Health Toolkit integration",
            buildConfig(chtBaseUrl)
        ));

        String couchdb = (couchdbUrl != null && !couchdbUrl.isEmpty()) ? couchdbUrl : null;
        this.chtApiClient = new CHTApiClient(chtBaseUrl, username, password, couchdb);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        logger.info("CHT Mediator Service initialized with baseUrl={}", chtBaseUrl);
    }

    // Visible-for-testing constructor
    CHTMediatorService(CHTApiClient chtApiClient) {
        super(new MediatorConfig(
            "CHT-Mediator", "1.0.0",
            "Mediator for Community Health Toolkit integration",
            buildConfig("http://localhost:5988")
        ));
        this.chtApiClient = chtApiClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    protected Object doProcessRequest(Object request, Map<String, String> headers, String requestId)
            throws MediatorException {
        try {
            String operation = headers.getOrDefault("X-Operation", "CREATE");
            String contactId = headers.get("X-Contact-Id");

            logger.info("Processing CHT request: operation={}, requestId={}", operation, requestId);

            switch (operation.toUpperCase()) {
                case "CREATE": {
                    CHTContact contact = convertToCHTContact(request);
                    CHTContact created = chtApiClient.createPerson(contact);
                    logger.info("Created person in CHT: requestId={}", requestId);
                    return created;
                }
                case "UPDATE": {
                    if (contactId == null || contactId.isEmpty()) {
                        throw new MediatorException(
                            "Contact ID required for UPDATE operation",
                            "CHT_MEDIATOR", "PROCESS_REQUEST", 400);
                    }
                    CHTContact contact = convertToCHTContact(request);
                    CHTContact updated = chtApiClient.updatePerson(contactId, contact);
                    logger.info("Updated person in CHT: contactId={}, requestId={}", contactId, requestId);
                    return updated;
                }
                case "GET": {
                    if (contactId == null || contactId.isEmpty()) {
                        throw new MediatorException(
                            "Contact ID required for GET operation",
                            "CHT_MEDIATOR", "PROCESS_REQUEST", 400);
                    }
                    CHTContact fetched = chtApiClient.getContact(contactId);
                    logger.info("Retrieved contact from CHT: contactId={}, requestId={}", contactId, requestId);
                    return fetched;
                }
                default:
                    throw new MediatorException(
                        "Unsupported operation: " + operation,
                        "CHT_MEDIATOR", "PROCESS_REQUEST", 400);
            }

        } catch (MediatorException e) {
            logger.error("Mediator error processing CHT request: requestId={}, error={}",
                requestId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error processing CHT request: requestId={}", requestId, e);
            throw new MediatorException(
                "Unexpected error processing CHT request: " + e.getMessage(),
                e, "CHT_MEDIATOR", "PROCESS_REQUEST", 500);
        }
    }

    @Override
    protected HealthCheckResult doHealthCheck() {
        long startTime = System.currentTimeMillis();
        try {
            boolean connected = chtApiClient.testConnection();
            long duration = System.currentTimeMillis() - startTime;
            if (connected) {
                logger.debug("CHT health check passed: duration={}ms", duration);
                return new HealthCheckResult(true, "CHT system is reachable", duration);
            } else {
                logger.warn("CHT health check failed: system not reachable");
                return new HealthCheckResult(false, "CHT system is not reachable", duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("CHT health check error: {}", e.getMessage(), e);
            return new HealthCheckResult(false, "Health check error: " + e.getMessage(), duration);
        }
    }

    @Override
    protected String doAuthenticate(Map<String, String> credentials) throws MediatorException {
        // CHT always uses Basic Auth
        logger.info("CHT authentication: Basic Auth");
        return "BASIC_AUTH";
    }

    @Override
    protected void validateRequest(Object request, Map<String, String> headers) throws MediatorException {
        super.validateRequest(request, headers);

        if (request == null) {
            throw new MediatorException(
                "Request body cannot be null",
                "CHT_MEDIATOR", "VALIDATE_REQUEST", 400);
        }

        String operation = headers.getOrDefault("X-Operation", "CREATE");
        if (!"CREATE".equalsIgnoreCase(operation) &&
            !"UPDATE".equalsIgnoreCase(operation) &&
            !"GET".equalsIgnoreCase(operation)) {
            throw new MediatorException(
                "Invalid operation: " + operation,
                "CHT_MEDIATOR", "VALIDATE_REQUEST", 400);
        }
    }

    @Override
    protected Map<String, String> getEndpoints() {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/cht/health");
        endpoints.put("contacts", "/cht/contacts");
        endpoints.put("reports", "/cht/reports");
        return endpoints;
    }

    @Override
    protected Map<String, Object> getDefaultChannelConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("name", "CHT Channel");
        config.put("urlPattern", "^/cht/.*$");
        config.put("type", "http");
        config.put("allow", new String[]{"admin", "cht-user"});
        config.put("routes", Map.of(
            "name", "CHT Mediator Route",
            "host", "localhost",
            "port", 8081,
            "path", "/cht",
            "primary", true
        ));
        return config;
    }

    private CHTContact convertToCHTContact(Object request) throws MediatorException {
        try {
            if (request instanceof CHTContact) {
                return (CHTContact) request;
            }
            String json = objectMapper.writeValueAsString(request);
            return objectMapper.readValue(json, CHTContact.class);
        } catch (Exception e) {
            throw new MediatorException(
                "Invalid request format: " + e.getMessage(),
                e, "CHT_MEDIATOR", "CONVERT_REQUEST", 400);
        }
    }

    private static Map<String, Object> buildConfig(String baseUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put("baseUrl", baseUrl);
        config.put("authType", "BASIC");
        return config;
    }

    protected CHTApiClient getChtApiClient() {
        return chtApiClient;
    }
}
