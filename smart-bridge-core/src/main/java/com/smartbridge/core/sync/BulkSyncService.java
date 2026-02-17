package com.smartbridge.core.sync;

import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.ucs.UCSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
public class BulkSyncService {
    private static final Logger logger = LoggerFactory.getLogger(BulkSyncService.class);
    private static final String SERVER_VERSION_FILE = "data/server-version.txt";
    
    private final String ucsBaseUrl;
    private final String username;
    private final String password;
    private final IngestionFlowService ingestionFlowService;
    private final RestTemplate restTemplate;
    
    public BulkSyncService(
            @Value("${smartbridge.ucs.api-url}") String ucsBaseUrl,
            @Value("${smartbridge.ucs.username:}") String username,
            @Value("${smartbridge.ucs.password:}") String password,
            IngestionFlowService ingestionFlowService) {
        this.ucsBaseUrl = ucsBaseUrl;
        this.username = username;
        this.password = password;
        this.ingestionFlowService = ingestionFlowService;
        this.restTemplate = new RestTemplate();
        ensureDataDirectory();
    }
    
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (username != null && !username.isEmpty()) {
            String auth = username + ":" + password;
            byte[] encodedAuth = java.util.Base64.getEncoder().encode(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set("Authorization", authHeader);
        }
        return headers;
    }
    
    @Scheduled(fixedDelayString = "${smartbridge.sync.interval-ms:300000}") // 5 minutes default
    public void incrementalSync() {
        logger.info("Starting incremental UCS to FHIR sync");
        long serverVersion = loadServerVersion();
        syncFromVersion(serverVersion);
    }
    
    public void bulkSync() {
        logger.info("Starting bulk UCS to FHIR sync from scratch");
        syncFromVersion(0L);
    }
    
    private void syncFromVersion(long serverVersion) {
        try {
            String url = ucsBaseUrl + "/rest/client/getAll?serverVersion=" + serverVersion;
            logger.info("Fetching clients from UCS: {}", url);
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> clients = (List<Map<String, Object>>) body.get("clients");
                
                if (clients != null && !clients.isEmpty()) {
                    logger.info("Received {} clients from UCS", clients.size());
                    
                    int successCount = 0;
                    int errorCount = 0;
                    long maxServerVersion = serverVersion;
                    
                    for (Map<String, Object> clientData : clients) {
                        try {
                            UCSClient ucsClient = mapToUCSClient(clientData);
                            ingestionFlowService.processIngestion(ucsClient);
                            successCount++;
                            
                            // Track max serverVersion
                            Object sv = clientData.get("serverVersion");
                            if (sv != null) {
                                long clientVersion = Long.parseLong(sv.toString());
                                maxServerVersion = Math.max(maxServerVersion, clientVersion);
                            }
                        } catch (Exception e) {
                            logger.error("Failed to sync client: {}", clientData.get("baseEntityId"), e);
                            errorCount++;
                        }
                    }
                    
                    saveServerVersion(maxServerVersion);
                    logger.info("Sync complete: {} success, {} errors, serverVersion={}", 
                        successCount, errorCount, maxServerVersion);
                } else {
                    logger.info("No new clients to sync");
                }
            }
        } catch (Exception e) {
            logger.error("Bulk sync failed", e);
        }
    }
    
    private UCSClient mapToUCSClient(Map<String, Object> data) {
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();
        identifiers.setOpensrpId((String) data.get("baseEntityId"));
        
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();
        demographics.setFirstName((String) data.get("firstName"));
        demographics.setLastName((String) data.get("lastName"));
        demographics.setGender((String) data.get("gender"));
        
        UCSClient client = new UCSClient();
        client.setIdentifiers(identifiers);
        client.setDemographics(demographics);
        return client;
    }
    
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
            Path dir = Paths.get("data");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }
}
