package com.smartbridge.core.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthCheckController.
 */
class HealthCheckControllerTest {

    private HealthCheckController controller;
    private Map<String, HealthIndicator> healthIndicators;

    @BeforeEach
    void setUp() {
        healthIndicators = new HashMap<>();
        controller = new HealthCheckController(healthIndicators);
    }

    @Test
    void testGetHealth_AllServicesHealthy() {
        // Given
        healthIndicators.put("testHealthIndicator", () -> Health.up()
                .withDetail("test", "value")
                .build());

        // When
        ResponseEntity<Map<String, Object>> response = controller.getHealth();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("overall"));
    }

    @Test
    void testGetHealth_ServiceDown() {
        // Given
        healthIndicators.put("testHealthIndicator", () -> Health.down()
                .withDetail("error", "Service unavailable")
                .build());

        // When
        ResponseEntity<Map<String, Object>> response = controller.getHealth();

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("overall"));
    }

    @Test
    void testLiveness_ReturnsOk() {
        // When
        ResponseEntity<Map<String, String>> response = controller.liveness();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("Service is alive", response.getBody().get("message"));
    }

    @Test
    void testReadiness_AllCriticalServicesReady() {
        // Given
        healthIndicators.put("FHIRHealthIndicator", () -> Health.up().build());
        healthIndicators.put("MessageQueueHealthIndicator", () -> Health.up().build());
        healthIndicators.put("TransformationHealthIndicator", () -> Health.up().build());

        // When
        ResponseEntity<Map<String, Object>> response = controller.readiness();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("READY", response.getBody().get("status"));
    }

    @Test
    void testReadiness_CriticalServiceDown() {
        // Given
        healthIndicators.put("FHIRHealthIndicator", () -> Health.down().build());
        healthIndicators.put("MessageQueueHealthIndicator", () -> Health.up().build());

        // When
        ResponseEntity<Map<String, Object>> response = controller.readiness();

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NOT_READY", response.getBody().get("status"));
    }

    @Test
    void testReadiness_NonCriticalServiceDown() {
        // Given
        healthIndicators.put("UCSHealthIndicator", () -> Health.down().build());
        healthIndicators.put("FHIRHealthIndicator", () -> Health.up().build());
        healthIndicators.put("MessageQueueHealthIndicator", () -> Health.up().build());
        healthIndicators.put("TransformationHealthIndicator", () -> Health.up().build());

        // When
        ResponseEntity<Map<String, Object>> response = controller.readiness();

        // Then
        // UCS is not critical for readiness, so should still be ready
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("READY", response.getBody().get("status"));
    }
}
