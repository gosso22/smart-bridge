package com.smartbridge.core.config;

import com.smartbridge.core.client.FHIRClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for FHIR client service.
 * Supports configuration via application properties.
 */
@Configuration
public class FHIRClientConfig {

    @Value("${smartbridge.fhir.server-url:}")
    private String fhirServerUrl;

    @Value("${smartbridge.fhir.auth-type:none}")
    private String authType;

    @Value("${smartbridge.fhir.auth-username:}")
    private String username;

    @Value("${smartbridge.fhir.auth-password:}")
    private String password;

    @Value("${smartbridge.fhir.auth-token:}")
    private String bearerToken;

    @Bean
    public FHIRClientService fhirClientService() {
        FHIRClientService clientService = new FHIRClientService();
        
        // Configure server URL
        if (fhirServerUrl != null && !fhirServerUrl.isEmpty()) {
            clientService.configure(fhirServerUrl);
            
            // Configure authentication if specified
            if ("basic".equalsIgnoreCase(authType)) {
                clientService.configureBasicAuth(username, password);
            } else if ("bearer".equalsIgnoreCase(authType)) {
                clientService.configureBearerToken(bearerToken);
            }
        }
        
        return clientService;
    }
}
