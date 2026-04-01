package com.smartbridge.config;

import com.smartbridge.mediators.cht.CHTApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for CHT API client.
 * CHT uses Basic Auth (medic/password standard).
 */
@Configuration
public class CHTClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(CHTClientConfig.class);

    @Value("${smartbridge.cht.api-url:http://localhost:5988}")
    private String chtApiUrl;

    @Value("${smartbridge.cht.username:medic}")
    private String username;

    @Value("${smartbridge.cht.password:password}")
    private String password;

    @Value("${smartbridge.cht.couchdb-url:}")
    private String couchdbUrl;

    @Bean
    public CHTApiClient chtApiClient() {
        logger.info("Creating CHT API client for URL: {}", chtApiUrl);
        String couchdb = (couchdbUrl != null && !couchdbUrl.isEmpty()) ? couchdbUrl : null;
        return new CHTApiClient(chtApiUrl, username, password, couchdb);
    }
}
