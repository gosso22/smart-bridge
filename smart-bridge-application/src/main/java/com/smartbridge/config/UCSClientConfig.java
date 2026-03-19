package com.smartbridge.config;

import com.smartbridge.mediators.ucs.UCSApiClient;
import com.smartbridge.mediators.ucs.UCSApiClient.AuthType;
import com.smartbridge.mediators.ucs.UCSApiClient.UCSAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for UCS API client.
 * Supports basic, token, and keycloak authentication types.
 */
@Configuration
public class UCSClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(UCSClientConfig.class);

    @Value("${smartbridge.ucs.api-url}")
    private String ucsApiUrl;

    @Value("${smartbridge.ucs.auth-type:basic}")
    private String authType;

    @Value("${smartbridge.ucs.username:}")
    private String username;

    @Value("${smartbridge.ucs.password:}")
    private String password;

    @Value("${smartbridge.ucs.token:}")
    private String token;

    @Value("${smartbridge.ucs.keycloak.token-url:}")
    private String keycloakTokenUrl;

    @Value("${smartbridge.ucs.keycloak.client-id:}")
    private String keycloakClientId;

    @Value("${smartbridge.ucs.keycloak.client-secret:}")
    private String keycloakClientSecret;

    @Bean
    public UCSApiClient ucsApiClient() {
        logger.info("Creating UCS API client for URL: {} with auth-type: {}", ucsApiUrl, authType);

        UCSAuthConfig authConfig;
        if ("basic".equalsIgnoreCase(authType)) {
            authConfig = new UCSAuthConfig(AuthType.BASIC, username, password);
        } else if ("keycloak".equalsIgnoreCase(authType)) {
            authConfig = new UCSAuthConfig(AuthType.KEYCLOAK, username, password);
            authConfig.setKeycloakTokenUrl(keycloakTokenUrl);
            authConfig.setKeycloakClientId(keycloakClientId);
            authConfig.setKeycloakClientSecret(keycloakClientSecret);
        } else {
            // Default to token
            if (token != null && !token.isEmpty()) {
                authConfig = new UCSAuthConfig(token);
            } else {
                authConfig = new UCSAuthConfig(AuthType.TOKEN, username, password);
            }
        }

        return new UCSApiClient(ucsApiUrl, authConfig);
    }
}
