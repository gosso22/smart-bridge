package com.smartbridge.core.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.ucs.UCSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Syncs clients from UCS (OpenSRP) to FHIR.
 *
 * Uses /rest/client/search to fetch clients and tracks the maximum
 * serverVersion for incremental sync on subsequent runs.
 */
@Service
public class BulkSyncService {
    private static final Logger logger = LoggerFactory.getLogger(BulkSyncService.class);
    private static final String DATA_DIR = "data";
    private static final String SERVER_VERSION_FILE = DATA_DIR + "/server-version.txt";
    private static final String CLIENT_SEARCH_PATH = "/rest/client/search";

    private final String ucsBaseUrl;
    private final String authType;
    private final String username;
    private final String password;
    private final String keycloakTokenUrl;
    private final String keycloakClientId;
    private final String keycloakClientSecret;
    private final int batchSize;
    private final IngestionFlowService ingestionFlowService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;

    public BulkSyncService(
            @Value("${smartbridge.ucs.api-url}") String ucsBaseUrl,
            @Value("${smartbridge.ucs.auth-type:basic}") String authType,
            @Value("${smartbridge.ucs.username:}") String username,
            @Value("${smartbridge.ucs.password:}") String password,
            @Value("${smartbridge.ucs.keycloak.token-url:}") String keycloakTokenUrl,
            @Value("${smartbridge.ucs.keycloak.client-id:}") String keycloakClientId,
            @Value("${smartbridge.ucs.keycloak.client-secret:}") String keycloakClientSecret,
            @Value("${smartbridge.sync.batch-size:1000}") int batchSize,
            IngestionFlowService ingestionFlowService) {
        this.ucsBaseUrl = ucsBaseUrl;
        this.authType = authType;
        this.username = username;
        this.password = password;
        this.keycloakTokenUrl = keycloakTokenUrl;
        this.keycloakClientId = keycloakClientId;
        this.keycloakClientSecret = keycloakClientSecret;
        this.batchSize = batchSize;
        this.ingestionFlowService = ingestionFlowService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        ensureDataDirectory();
    }

    // ===== Public sync methods =====

    @Scheduled(fixedDelayString = "${smartbridge.sync.interval-ms:300000}",
               initialDelayString = "${smartbridge.sync.initial-delay-ms:30000}")
    public void incrementalSync() {
        logger.info("Starting incremental UCS to FHIR sync");
        long serverVersion = loadServerVersion();
        syncClients(serverVersion);
    }

    public void bulkSync() {
        logger.info("Starting full bulk sync from scratch");
        saveServerVersion(0L);
        syncClients(0L);
    }

    // ===== Core sync logic =====

    /**
     * Fetch clients from OpenSRP /rest/client/search and ingest into FHIR.
     * Paginates using serverVersion — each batch returns clients with
     * serverVersion >= the requested value. We track the max and continue
     * until fewer than batchSize results are returned.
     */
    private void syncClients(long fromServerVersion) {
        int totalSuccess = 0;
        int totalErrors = 0;
        long currentVersion = fromServerVersion;
        boolean hasMore = true;

        try {
            while (hasMore) {
                String url = ucsBaseUrl + CLIENT_SEARCH_PATH
                    + "?serverVersion=" + currentVersion;
                logger.info("Fetching clients from UCS: {}", url);

                ResponseEntity<String> response = fetchWithAuthRetry(url);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    logger.warn("Unexpected response from UCS: {}", response.getStatusCode());
                    break;
                }

                List<UCSClient> clients = objectMapper.readValue(
                    response.getBody(), new TypeReference<List<UCSClient>>() {});

                if (clients == null || clients.isEmpty()) {
                    logger.info("No new clients to sync");
                    break;
                }

                logger.info("Received {} clients from UCS", clients.size());

                // Find max serverVersion in this batch
                long batchMaxVersion = currentVersion;
                for (UCSClient client : clients) {
                    if (client.getServerVersion() != null) {
                        batchMaxVersion = Math.max(batchMaxVersion, client.getServerVersion());
                    }
                }

                // Process each client
                for (UCSClient client : clients) {
                    try {
                        ingestionFlowService.processIngestion(client);
                        totalSuccess++;
                    } catch (Exception e) {
                        logger.error("Failed to sync client: {}", client.getBaseEntityId(), e);
                        totalErrors++;
                    }
                }

                // Save progress
                saveServerVersion(batchMaxVersion);

                // If max version didn't advance or fewer results than batch size, stop
                if (batchMaxVersion <= currentVersion || clients.size() < batchSize) {
                    hasMore = false;
                } else {
                    // Move past this batch for next iteration
                    currentVersion = batchMaxVersion + 1;
                }
            }

            logger.info("Sync complete: {} clients synced, {} errors, serverVersion={}",
                totalSuccess, totalErrors, currentVersion);

        } catch (Exception e) {
            logger.error("Bulk sync failed", e);
        }
    }

    // ===== Authentication =====

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if ("basic".equalsIgnoreCase(authType)) {
            if (username != null && !username.isEmpty()) {
                String auth = username + ":" + password;
                String encoded = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                headers.set("Authorization", "Basic " + encoded);
            }
        } else if ("keycloak".equalsIgnoreCase(authType)) {
            String token = getKeycloakToken();
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
            }
        } else {
            String token = getLoginToken();
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
            }
        }

        return headers;
    }

    private String getKeycloakToken() {
        if (cachedToken != null) return cachedToken;
        try {
            logger.info("Authenticating with Keycloak at: {}", keycloakTokenUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", keycloakClientId);
            formData.add("username", username);
            formData.add("password", password);
            if (keycloakClientSecret != null && !keycloakClientSecret.isEmpty()) {
                formData.add("client_secret", keycloakClientSecret);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                keycloakTokenUrl, HttpMethod.POST, request,
                (Class<Map<String, Object>>)(Class<?>)Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("access_token")) {
                cachedToken = (String) body.get("access_token");
                logger.info("Successfully obtained Keycloak token");
                return cachedToken;
            }
        } catch (Exception e) {
            logger.error("Keycloak authentication failed: {}", e.getMessage(), e);
        }
        return null;
    }

    private String getLoginToken() {
        if (cachedToken != null) return cachedToken;
        try {
            String url = ucsBaseUrl + "/auth/login";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> credentials = Map.of(
                "username", username, "password", password);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(credentials, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, request,
                (Class<Map<String, Object>>)(Class<?>)Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("token")) {
                cachedToken = (String) body.get("token");
                logger.info("Successfully obtained login token");
                return cachedToken;
            }
        } catch (Exception e) {
            logger.error("Token authentication failed: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Fetch URL with auth, retrying once on 401/403 with a fresh token.
     */
    private ResponseEntity<String> fetchWithAuthRetry(String url) {
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                logger.warn("Auth failed ({}), clearing token and retrying", e.getStatusCode());
                cachedToken = null;
                HttpHeaders retryHeaders = createAuthHeaders();
                HttpEntity<Void> retryRequest = new HttpEntity<>(retryHeaders);
                return restTemplate.exchange(url, HttpMethod.GET, retryRequest, String.class);
            }
            throw e;
        }
    }

    // ===== Server version persistence =====

    private long loadServerVersion() {
        try {
            Path path = Paths.get(SERVER_VERSION_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                return Long.parseLong(content);
            }
        } catch (IOException | NumberFormatException e) {
            logger.warn("Could not load server version, starting from 0", e);
        }
        return 0L;
    }

    private void saveServerVersion(long version) {
        try {
            Path path = Paths.get(SERVER_VERSION_FILE);
            Files.writeString(path, String.valueOf(version));
            logger.debug("Saved serverVersion: {}", version);
        } catch (IOException e) {
            logger.error("Failed to save server version", e);
        }
    }

    private void ensureDataDirectory() {
        try {
            Path dir = Paths.get(DATA_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }
}
