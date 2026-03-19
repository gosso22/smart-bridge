package com.smartbridge.mediators.ucs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.ucs.UCSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

/**
 * Client for interacting with UCS (OpenSRP) system API.
 * Supports basic authentication, token-based authentication, and Keycloak OAuth2.
 */
public class UCSApiClient {

    private static final Logger logger = LoggerFactory.getLogger(UCSApiClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final UCSAuthConfig authConfig;

    public UCSApiClient(String baseUrl, UCSAuthConfig authConfig) {
        this.baseUrl = baseUrl;
        this.authConfig = authConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    public UCSClient createClient(UCSClient client) throws MediatorException {
        String url = baseUrl + "/clients";

        try {
            logger.info("Creating client in UCS: baseEntityId={}", client.getBaseEntityId());

            HttpHeaders headers = buildHeaders();
            HttpEntity<UCSClient> request = new HttpEntity<>(client, headers);

            ResponseEntity<UCSClient> response = restTemplate.exchange(
                url, HttpMethod.POST, request, UCSClient.class);

            logger.info("Successfully created client in UCS: status={}", response.getStatusCode());
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error creating client in UCS: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new MediatorException(
                "Failed to create client in UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "CREATE_CLIENT", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error creating client in UCS: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error creating client in UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "CREATE_CLIENT", 500);
        }
    }

    public UCSClient updateClient(String clientId, UCSClient client) throws MediatorException {
        String url = baseUrl + "/clients/" + clientId;

        try {
            logger.info("Updating client in UCS: clientId={}", clientId);

            HttpHeaders headers = buildHeaders();
            HttpEntity<UCSClient> request = new HttpEntity<>(client, headers);

            ResponseEntity<UCSClient> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, UCSClient.class);

            logger.info("Successfully updated client in UCS: status={}", response.getStatusCode());
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error updating client in UCS: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new MediatorException(
                "Failed to update client in UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "UPDATE_CLIENT", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error updating client in UCS: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error updating client in UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "UPDATE_CLIENT", 500);
        }
    }

    public UCSClient getClient(String clientId) throws MediatorException {
        String url = baseUrl + "/clients/" + clientId;

        try {
            logger.info("Retrieving client from UCS: clientId={}", clientId);

            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UCSClient> response = restTemplate.exchange(
                url, HttpMethod.GET, request, UCSClient.class);

            logger.info("Successfully retrieved client from UCS: status={}", response.getStatusCode());
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error retrieving client from UCS: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new MediatorException(
                "Failed to retrieve client from UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "GET_CLIENT", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error retrieving client from UCS: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error retrieving client from UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "GET_CLIENT", 500);
        }
    }

    /**
     * Authenticate with UCS system and obtain a token.
     * For TOKEN auth: calls /auth/login with credentials.
     * For KEYCLOAK auth: calls the Keycloak token endpoint.
     */
    public String authenticate() throws MediatorException {
        if (authConfig.getAuthType() == AuthType.TOKEN && authConfig.getToken() != null) {
            logger.debug("Using pre-configured token authentication");
            return authConfig.getToken();
        }

        if (authConfig.getAuthType() == AuthType.BASIC) {
            logger.debug("Using basic authentication");
            return null;
        }

        if (authConfig.getAuthType() == AuthType.KEYCLOAK) {
            return authenticateKeycloak();
        }

        // TOKEN auth without pre-configured token — authenticate via /auth/login
        String url = baseUrl + "/auth/login";

        try {
            logger.info("Authenticating with UCS system");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> credentials = Map.of(
                "username", authConfig.getUsername(),
                "password", authConfig.getPassword()
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(credentials, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, request,
                (Class<Map<String, Object>>)(Class<?>)Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("token")) {
                String token = (String) body.get("token");
                logger.info("Successfully authenticated with UCS system");
                return token;
            }

            throw new MediatorException(
                "Authentication response missing token",
                "UCS_MEDIATOR", "AUTHENTICATE", 500);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error authenticating with UCS: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new MediatorException(
                "Failed to authenticate with UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "AUTHENTICATE", e.getStatusCode().value());
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error authenticating with UCS: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error authenticating with UCS: " + e.getMessage(),
                e, "UCS_MEDIATOR", "AUTHENTICATE", 500);
        }
    }

    /**
     * Authenticate via Keycloak OAuth2 token endpoint.
     */
    private String authenticateKeycloak() throws MediatorException {
        String tokenUrl = authConfig.getKeycloakTokenUrl();
        if (tokenUrl == null || tokenUrl.isEmpty()) {
            throw new MediatorException(
                "Keycloak token URL is not configured",
                "UCS_MEDIATOR", "AUTHENTICATE_KEYCLOAK", 500);
        }

        try {
            logger.info("Authenticating with Keycloak at: {}", tokenUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", authConfig.getKeycloakClientId());
            formData.add("username", authConfig.getUsername());
            formData.add("password", authConfig.getPassword());

            if (authConfig.getKeycloakClientSecret() != null && !authConfig.getKeycloakClientSecret().isEmpty()) {
                formData.add("client_secret", authConfig.getKeycloakClientSecret());
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request,
                (Class<Map<String, Object>>)(Class<?>)Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("access_token")) {
                String token = (String) body.get("access_token");
                logger.info("Successfully authenticated with Keycloak");
                return token;
            }

            throw new MediatorException(
                "Keycloak response missing access_token",
                "UCS_MEDIATOR", "AUTHENTICATE_KEYCLOAK", 500);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("Keycloak auth failed: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new MediatorException(
                "Failed to authenticate with Keycloak: " + e.getMessage(),
                e, "UCS_MEDIATOR", "AUTHENTICATE_KEYCLOAK", e.getStatusCode().value());
        } catch (MediatorException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error authenticating with Keycloak: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error authenticating with Keycloak: " + e.getMessage(),
                e, "UCS_MEDIATOR", "AUTHENTICATE_KEYCLOAK", 500);
        }
    }

    private HttpHeaders buildHeaders() throws MediatorException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (authConfig.getAuthType() == AuthType.BASIC) {
            String auth = authConfig.getUsername() + ":" + authConfig.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            logger.debug("Using basic authentication");
        } else if (authConfig.getAuthType() == AuthType.TOKEN || authConfig.getAuthType() == AuthType.KEYCLOAK) {
            String token = authConfig.getToken();
            if (token == null) {
                token = authenticate();
                authConfig.setToken(token);
            }
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
                logger.debug("Using bearer token authentication");
            }
        }

        return headers;
    }

    public boolean testConnection() {
        try {
            String url = baseUrl + "/health";
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("UCS connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Authentication type enum.
     */
    public enum AuthType {
        BASIC,
        TOKEN,
        KEYCLOAK
    }

    /**
     * Authentication configuration supporting Basic, Token, and Keycloak auth.
     */
    public static class UCSAuthConfig {
        private AuthType authType;
        private String username;
        private String password;
        private String token;
        private String keycloakTokenUrl;
        private String keycloakClientId;
        private String keycloakClientSecret;

        public UCSAuthConfig(AuthType authType, String username, String password) {
            this.authType = authType;
            this.username = username;
            this.password = password;
        }

        public UCSAuthConfig(String token) {
            this.authType = AuthType.TOKEN;
            this.token = token;
        }

        public AuthType getAuthType() { return authType; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public String getKeycloakTokenUrl() { return keycloakTokenUrl; }
        public void setKeycloakTokenUrl(String keycloakTokenUrl) { this.keycloakTokenUrl = keycloakTokenUrl; }

        public String getKeycloakClientId() { return keycloakClientId; }
        public void setKeycloakClientId(String keycloakClientId) { this.keycloakClientId = keycloakClientId; }

        public String getKeycloakClientSecret() { return keycloakClientSecret; }
        public void setKeycloakClientSecret(String keycloakClientSecret) { this.keycloakClientSecret = keycloakClientSecret; }
    }
}
