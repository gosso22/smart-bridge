package com.smartbridge.core.monitoring.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Health indicator for HAPI FHIR server connectivity.
 * Checks if the FHIR server is reachable and responding.
 */
@Component
public class FHIRHealthIndicator implements HealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(FHIRHealthIndicator.class);

    @Value("${smartbridge.fhir.server-url:http://localhost:8082/fhir}")
    private String fhirServerUrl;

    @Value("${smartbridge.fhir.timeout:30000}")
    private int timeout;

    private final RestTemplate restTemplate;

    public FHIRHealthIndicator() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Health health() {
        try {
            // Try to access the FHIR server metadata endpoint
            String metadataUrl = fhirServerUrl + "/metadata";
            long startTime = System.currentTimeMillis();
            
            restTemplate.getForObject(metadataUrl, String.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.debug("FHIR server health check successful, response time: {}ms", responseTime);
            
            return Health.up()
                    .withDetail("fhir-server", fhirServerUrl)
                    .withDetail("response-time-ms", responseTime)
                    .withDetail("status", "reachable")
                    .build();
        } catch (Exception e) {
            logger.error("FHIR server health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("fhir-server", fhirServerUrl)
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "unreachable")
                    .build();
        }
    }
}
