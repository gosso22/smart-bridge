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
 */
@Configuration
public class UCSClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(UCSClientConfig.class);

    @Value("${smartbridge.ucs.api-url}")
    private String ucsApiUrl;

    @Value("${smartbridge.ucs.auth-type:token}")
    private String authType;

    @Value("${smartbridge.ucs.username:}")
    private String username;

    @Value("${smartbridge.ucs.password:}")
    private String password;

    @Value("${smartbridge.ucs.token:}")
    private String token;

    @Bean
    public UCSApiClient ucsApiClient() {
        logger.info("Creating UCS API client for URL: {}", ucsApiUrl);
        
        UCSAuthConfig authConfig;
        if ("basic".equalsIgnoreCase(authType)) {
            authConfig = new UCSAuthConfig(AuthType.BASIC, username, password);
        } else {
            authConfig = new UCSAuthConfig(token);
        }
        
        return new UCSApiClient(ucsApiUrl, authConfig);
    }
}
