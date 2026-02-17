package com.smartbridge.core.monitoring.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransformationHealthIndicator.
 */
class TransformationHealthIndicatorTest {

    private TransformationHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new TransformationHealthIndicator();
    }

    @Test
    void testHealth_ReturnsHealthStatus() {
        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health);
        assertNotNull(health.getStatus());
        assertTrue(health.getDetails().containsKey("service"));
        assertEquals("transformation", health.getDetails().get("service"));
    }

    @Test
    void testHealth_IncludesMemoryMetrics() {
        // When
        Health health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().containsKey("memory-used-mb"));
        assertTrue(health.getDetails().containsKey("memory-max-mb"));
        assertTrue(health.getDetails().containsKey("memory-usage-percent"));
        assertTrue(health.getDetails().containsKey("status"));
    }

    @Test
    void testHealth_StatusIsUpWhenMemoryNormal() {
        // When
        Health health = healthIndicator.health();

        // Then
        // Under normal conditions, memory should not be critical
        // Status should be UP unless memory is critically high
        if ("healthy".equals(health.getDetails().get("status")) || 
            "warning".equals(health.getDetails().get("status"))) {
            assertEquals(Status.UP, health.getStatus());
        }
    }
}
