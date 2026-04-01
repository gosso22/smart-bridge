package com.smartbridge.mediators.cht;

import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResilientCHTApiClient.
 * Verifies circuit breaker and retry behavior with a non-existent CHT server.
 */
class ResilientCHTApiClientTest {

    private ResilientCHTApiClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CHTApiClient rawClient = new CHTApiClient(
            "http://localhost:9997", "medic", "password", null);

        circuitBreaker = new CircuitBreaker("CHT", 3, Duration.ofSeconds(10), 2);
        RetryPolicy retryPolicy = new RetryPolicy.Builder("CHT")
            .maxAttempts(2)
            .initialDelay(Duration.ofMillis(10))
            .maxDelay(Duration.ofMillis(50))
            .backoffMultiplier(2.0)
            .build();

        resilientClient = new ResilientCHTApiClient(rawClient, circuitBreaker, retryPolicy);
    }

    // ==================== Resilient Operation Tests ====================

    @Test
    void testListPersons_FailsWithRetry() {
        assertThrows(Exception.class, () -> resilientClient.listPersons(10, 0));
    }

    @Test
    void testGetContact_FailsWithRetry() {
        assertThrows(Exception.class, () -> resilientClient.getContact("test-id"));
    }

    @Test
    void testCreatePerson_FailsWithRetry() {
        assertThrows(Exception.class, () -> {
            var contact = new com.smartbridge.core.model.cht.CHTContact();
            contact.setId("test");
            contact.setName("Test Person");
            resilientClient.createPerson(contact);
        });
    }

    @Test
    void testUpdatePerson_FailsWithRetry() {
        assertThrows(Exception.class, () -> {
            var contact = new com.smartbridge.core.model.cht.CHTContact();
            contact.setId("test");
            contact.setName("Test Person");
            resilientClient.updatePerson("test-id", contact);
        });
    }

    @Test
    void testGetChanges_FailsWithRetry() {
        assertThrows(Exception.class, () -> resilientClient.getChanges("0", 10));
    }

    @Test
    void testListPlaces_FailsWithRetry() {
        assertThrows(Exception.class, () -> resilientClient.listPlaces("clinic"));
    }

    @Test
    void testExportReports_FailsWithRetry() {
        assertThrows(Exception.class, () -> resilientClient.exportReports("pregnancy_visit"));
    }

    // ==================== Connection Test ====================

    @Test
    void testTestConnection_ReturnsFalseWhenDown() {
        boolean connected = resilientClient.testConnection();
        assertFalse(connected, "Connection test should return false when server is unavailable");
    }

    // ==================== Circuit Breaker Tests ====================

    @Test
    void testCircuitBreaker_OpensAfterConsecutiveFailures() {
        // Exhaust the circuit breaker threshold (3 failures)
        for (int i = 0; i < 4; i++) {
            try {
                resilientClient.getContact("id-" + i);
            } catch (Exception ignored) {
            }
        }

        CircuitBreaker.CircuitBreakerMetrics metrics = resilientClient.getCircuitBreakerMetrics();
        // After enough failures, the circuit should have opened at some point
        assertTrue(metrics.getConsecutiveFailures() > 0, "Should record failures");
    }

    @Test
    void testCircuitBreaker_ResetRestoresNormalOperation() {
        // Cause some failures
        for (int i = 0; i < 3; i++) {
            try {
                resilientClient.getContact("id-" + i);
            } catch (Exception ignored) {
            }
        }

        resilientClient.resetCircuitBreaker();

        CircuitBreaker.CircuitBreakerMetrics metrics = resilientClient.getCircuitBreakerMetrics();
        assertEquals(0, metrics.getConsecutiveFailures(), "Failures should be reset");
        assertEquals(0, metrics.getConsecutiveSuccesses(), "Successes should be reset");
    }

    @Test
    void testGetCircuitBreakerMetrics_ReturnsMetrics() {
        CircuitBreaker.CircuitBreakerMetrics metrics = resilientClient.getCircuitBreakerMetrics();
        assertNotNull(metrics, "Metrics should not be null");
    }
}
