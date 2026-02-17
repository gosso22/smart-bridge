package com.smartbridge.core.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for health check endpoints.
 * Provides detailed health information for all Smart Bridge services.
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    private final Map<String, HealthIndicator> healthIndicators;

    public HealthCheckController(Map<String, HealthIndicator> healthIndicators) {
        this.healthIndicators = healthIndicators;
    }

    /**
     * Get overall system health status.
     *
     * @return Health status of all services
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> healthStatus = new HashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, HealthIndicator> entry : healthIndicators.entrySet()) {
            String name = entry.getKey().replace("HealthIndicator", "");
            Health health = entry.getValue().health();
            
            Map<String, Object> serviceHealth = new HashMap<>();
            serviceHealth.put("status", health.getStatus().getCode());
            serviceHealth.put("details", health.getDetails());
            
            healthStatus.put(name, serviceHealth);
            
            if (!"UP".equals(health.getStatus().getCode())) {
                allHealthy = false;
            }
        }

        healthStatus.put("overall", allHealthy ? "UP" : "DOWN");
        
        return allHealthy ? ResponseEntity.ok(healthStatus) : ResponseEntity.status(503).body(healthStatus);
    }

    /**
     * Simple liveness probe endpoint.
     *
     * @return 200 OK if service is alive
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Service is alive");
        return ResponseEntity.ok(response);
    }

    /**
     * Readiness probe endpoint.
     *
     * @return 200 OK if service is ready to accept traffic
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        boolean ready = true;

        // Check critical services
        for (Map.Entry<String, HealthIndicator> entry : healthIndicators.entrySet()) {
            String name = entry.getKey();
            // Only check critical services for readiness
            if (name.contains("FHIR") || name.contains("MessageQueue") || name.contains("Transformation")) {
                Health health = entry.getValue().health();
                if (!"UP".equals(health.getStatus().getCode())) {
                    ready = false;
                    response.put(name, "DOWN");
                }
            }
        }

        response.put("status", ready ? "READY" : "NOT_READY");
        
        return ready ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
    }
}
