package com.smartbridge.mediators.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of OpenHIM client for mediator registration and communication.
 * Handles HTTP communication with OpenHIM Core for registration, heartbeat, and transaction reporting.
 */
@Component
public class OpenHIMClientImpl implements OpenHIMClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenHIMClientImpl.class);

    @Value("${openhim.core.url:http://localhost:8080}")
    private String openHIMCoreUrl;

    @Value("${openhim.core.username:root@openhim.org}")
    private String openHIMUsername;

    @Value("${openhim.core.password:openhim-password}")
    private String openHIMPassword;

    @Value("${openhim.enabled:false}")
    private boolean openHIMEnabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenHIMClientImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean registerMediator(MediatorRegistration registration) {
        if (!openHIMEnabled) {
            logger.info("OpenHIM integration disabled, skipping mediator registration");
            return false;
        }

        try {
            logger.info("Registering mediator {} with OpenHIM at {}", 
                registration.getName(), openHIMCoreUrl);

            String url = openHIMCoreUrl + "/mediators";
            
            Map<String, Object> registrationData = new HashMap<>();
            registrationData.put("urn", "urn:mediator:" + registration.getName());
            registrationData.put("version", registration.getVersion());
            registrationData.put("name", registration.getName());
            registrationData.put("description", registration.getDescription());
            registrationData.put("endpoints", registration.getEndpoints());
            registrationData.put("defaultChannelConfig", registration.getDefaultChannelConfig());

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registrationData, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            
            if (success) {
                logger.info("Successfully registered mediator {} with OpenHIM", registration.getName());
            } else {
                logger.error("Failed to register mediator {}, status: {}", 
                    registration.getName(), response.getStatusCode());
            }

            return success;

        } catch (Exception e) {
            logger.error("Error registering mediator {} with OpenHIM: {}", 
                registration.getName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendHeartbeat(String mediatorName) {
        if (!openHIMEnabled) {
            return false;
        }

        try {
            String url = openHIMCoreUrl + "/mediators/" + mediatorName + "/heartbeat";
            
            Map<String, Object> heartbeatData = new HashMap<>();
            heartbeatData.put("uptime", System.currentTimeMillis());

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(heartbeatData, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            logger.debug("Error sending heartbeat for mediator {}: {}", mediatorName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean reportTransaction(TransactionReport transaction) {
        if (!openHIMEnabled) {
            return false;
        }

        try {
            String url = openHIMCoreUrl + "/transactions";
            
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("transactionId", transaction.getTransactionId());
            transactionData.put("mediatorName", transaction.getMediatorName());
            transactionData.put("status", transaction.getStatus());
            transactionData.put("httpStatusCode", transaction.getHttpStatusCode());
            transactionData.put("startTime", transaction.getStartTime().toString());
            transactionData.put("endTime", transaction.getEndTime().toString());
            
            if (transaction.getRequest() != null) {
                transactionData.put("request", transaction.getRequest());
            }
            
            if (transaction.getResponse() != null) {
                transactionData.put("response", transaction.getResponse());
            }
            
            if (transaction.getError() != null) {
                transactionData.put("error", transaction.getError());
            }

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(transactionData, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            logger.debug("Error reporting transaction {}: {}", 
                transaction.getTransactionId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isOpenHIMReachable() {
        if (!openHIMEnabled) {
            return false;
        }

        try {
            String url = openHIMCoreUrl + "/heartbeat";
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            logger.debug("OpenHIM Core not reachable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create HTTP headers with basic authentication for OpenHIM Core.
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        // Basic authentication
        String auth = openHIMUsername + ":" + openHIMPassword;
        byte[] encodedAuth = java.util.Base64.getEncoder().encode(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);
        
        headers.set("Authorization", authHeader);
        
        return headers;
    }
}
