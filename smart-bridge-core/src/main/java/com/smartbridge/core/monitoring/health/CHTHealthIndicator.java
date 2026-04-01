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
 * Health indicator for CHT system connectivity.
 * Checks if the CHT API is reachable and responding.
 */
@Component
public class CHTHealthIndicator implements HealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(CHTHealthIndicator.class);

    @Value("${smartbridge.cht.api-url:http://localhost:5988}")
    private String chtApiUrl;

    private final RestTemplate restTemplate;

    public CHTHealthIndicator() {
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        logger.info("CHTHealthIndicator initialized with URL: {}", chtApiUrl);
    }

    @Override
    public Health health() {
        try {
            String healthUrl = chtApiUrl + "/api/v1/health";
            long startTime = System.currentTimeMillis();

            restTemplate.getForObject(healthUrl, String.class);

            long responseTime = System.currentTimeMillis() - startTime;

            logger.debug("CHT health check successful, response time: {}ms", responseTime);

            return Health.up()
                    .withDetail("cht-api", chtApiUrl)
                    .withDetail("response-time-ms", responseTime)
                    .withDetail("status", "reachable")
                    .build();
        } catch (Exception e) {
            logger.warn("CHT health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("cht-api", chtApiUrl)
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "unreachable")
                    .build();
        }
    }
}
