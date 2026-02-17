package com.smartbridge.core.monitoring.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Health indicator for UCS system connectivity.
 * Checks if the UCS API is reachable and responding.
 */
@Component
public class UCSHealthIndicator implements HealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(UCSHealthIndicator.class);

    @Value("${UCS_API_URL:http://localhost:8081/ucs}")
    private String ucsApiUrl;

    @Value("${UCS_TIMEOUT:10000}")
    private int timeout;

    private final RestTemplate restTemplate;

    public UCSHealthIndicator() {
        this.restTemplate = new RestTemplate();
    }
    
    @PostConstruct
    public void init() {
        logger.info("UCSHealthIndicator initialized with URL: {}", ucsApiUrl);
    }

    @Override
    public Health health() {
        try {
            // Try to access the UCS health endpoint
            String healthUrl = ucsApiUrl + "/health";
            long startTime = System.currentTimeMillis();
            
            restTemplate.getForObject(healthUrl, String.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.debug("UCS system health check successful, response time: {}ms", responseTime);
            
            return Health.up()
                    .withDetail("ucs-api", ucsApiUrl)
                    .withDetail("response-time-ms", responseTime)
                    .withDetail("status", "reachable")
                    .build();
        } catch (Exception e) {
            logger.warn("UCS system health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("ucs-api", ucsApiUrl)
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "unreachable")
                    .build();
        }
    }
}
