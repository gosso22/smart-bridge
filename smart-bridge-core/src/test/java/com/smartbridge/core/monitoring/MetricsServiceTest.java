package com.smartbridge.core.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsService.
 */
class MetricsServiceTest {

    private MetricsService metricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void testRecordTransformation_Success() throws Exception {
        // Given
        Callable<String> operation = () -> "transformed";

        // When
        String result = metricsService.recordTransformation("ucs", "fhir", operation);

        // Then
        assertEquals("transformed", result);
        assertNotNull(meterRegistry.find("smart_bridge_transformation_duration").timer());
        assertNotNull(meterRegistry.find("smart_bridge_transformation_success_total").counter());
    }

    @Test
    void testRecordTransformation_Failure() {
        // Given
        Callable<String> operation = () -> {
            throw new RuntimeException("Transformation failed");
        };

        // When/Then
        assertThrows(RuntimeException.class, () -> 
            metricsService.recordTransformation("ucs", "fhir", operation)
        );
        
        assertNotNull(meterRegistry.find("smart_bridge_transformation_duration").timer());
        assertNotNull(meterRegistry.find("smart_bridge_transformation_error_total").counter());
    }

    @Test
    void testRecordMediatorOperation_Success() throws Exception {
        // Given
        Callable<String> operation = () -> "mediated";

        // When
        String result = metricsService.recordMediatorOperation("ucs", "read", operation);

        // Then
        assertEquals("mediated", result);
        assertNotNull(meterRegistry.find("smart_bridge_mediator_duration").timer());
        assertNotNull(meterRegistry.find("smart_bridge_mediator_operations_total").counter());
    }

    @Test
    void testRecordFHIROperation() {
        // When
        metricsService.recordFHIROperation("Patient", "POST", true);

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_fhir_operations_total")
                .tag("resource", "Patient")
                .tag("operation", "POST")
                .tag("status", "success")
                .counter());
    }

    @Test
    void testRecordUCSOperation() {
        // When
        metricsService.recordUCSOperation("/clients", "GET", true);

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_ucs_operations_total")
                .tag("endpoint", "/clients")
                .tag("method", "GET")
                .tag("status", "success")
                .counter());
    }

    @Test
    void testRecordAuditLog() {
        // When
        metricsService.recordAuditLog("data_access");

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_audit_logs_total")
                .tag("event_type", "data_access")
                .counter());
    }

    @Test
    void testRecordSecurityEvent() {
        // When
        metricsService.recordSecurityEvent("authentication_failure", "high");

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_security_events_total")
                .tag("event_type", "authentication_failure")
                .tag("severity", "high")
                .counter());
    }

    @Test
    void testRecordQueueOperation() {
        // When
        metricsService.recordQueueOperation("transformation", "enqueue");

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_queue_operations_total")
                .tag("queue", "transformation")
                .tag("operation", "enqueue")
                .counter());
    }

    @Test
    void testRecordThroughput() {
        // When
        metricsService.recordThroughput(100, Duration.ofSeconds(10));

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_transformation_throughput").gauge());
    }

    @Test
    void testRecordCircuitBreakerState() {
        // When
        metricsService.recordCircuitBreakerState("fhir-service", "open");

        // Then
        assertNotNull(meterRegistry.find("smart_bridge_circuit_breaker_state_changes_total")
                .tag("service", "fhir-service")
                .tag("state", "open")
                .counter());
    }
}
