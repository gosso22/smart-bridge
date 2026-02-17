package com.smartbridge.core.monitoring;

import com.smartbridge.core.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertingServiceTest {

    @Mock
    private MetricsService metricsService;

    @Mock
    private AuditService auditService;

    private AlertingService alertingService;

    @BeforeEach
    void setUp() {
        alertingService = new AlertingService(metricsService, auditService);
    }

    @Test
    void testAlertTransformationFailure() {
        // When
        alertingService.alertTransformationFailure("ucs", "fhir", "Invalid data format", "client-123");

        // Then
        verify(metricsService).recordSecurityEvent("transformation_failure", "medium");
        verify(auditService).logTransformation(
            eq("SYSTEM"),
            eq("ucs"),
            eq("fhir"),
            eq("TRANSFORM_FAILED"),
            eq("client-123"),
            isNull(),
            eq(false),
            eq("Invalid data format")
        );
    }

    @Test
    void testAlertFHIRServerUnavailable() {
        // When
        alertingService.alertFHIRServerUnavailable("http://fhir.example.com", "Connection timeout");

        // Then
        verify(metricsService).recordSecurityEvent("fhir_unavailable", "high");
    }

    @Test
    void testAlertUCSApiUnavailable() {
        // When
        alertingService.alertUCSApiUnavailable("/api/clients", "503 Service Unavailable");

        // Then
        verify(metricsService).recordSecurityEvent("ucs_unavailable", "high");
    }

    @Test
    void testAlertCircuitBreakerOpen() {
        // When
        alertingService.alertCircuitBreakerOpen("fhir-service", 5);

        // Then
        verify(metricsService).recordCircuitBreakerState("fhir-service", "open");
    }

    @Test
    void testAlertQueueOverflow() {
        // When
        alertingService.alertQueueOverflow("transformation-queue", 950, 1000);

        // Then
        verify(metricsService).recordQueueOperation("transformation-queue", "overflow_warning");
    }

    @Test
    void testAlertDeadLetterQueue() {
        // When
        alertingService.alertDeadLetterQueue("retry-queue", "msg-456", "Max retries exceeded");

        // Then
        verify(metricsService).recordQueueOperation("dead-letter", "message_added");
    }

    @Test
    void testAlertPerformanceDegradation() {
        // When
        alertingService.alertPerformanceDegradation("transformation", 8000, 5000);

        // Then - Should trigger alert (no exception thrown)
        assertDoesNotThrow(() -> 
            alertingService.alertPerformanceDegradation("transformation", 8000, 5000)
        );
    }

    @Test
    void testDetectAuthenticationFailure_SingleFailure() {
        // When
        alertingService.detectAuthenticationFailure("user123", "John Doe", "192.168.1.1", 
            "basic", "Invalid password");

        // Then
        verify(auditService).logAuthenticationAttempt(
            eq("user123"),
            eq("John Doe"),
            eq("basic"),
            eq("192.168.1.1"),
            eq(false),
            eq("Invalid password")
        );
        
        // Should not trigger alert yet
        verify(metricsService, never()).recordSecurityEvent(anyString(), anyString());
    }

    @Test
    void testDetectAuthenticationFailure_MultipleFailures() {
        // When - Trigger 3 failures
        for (int i = 0; i < 3; i++) {
            alertingService.detectAuthenticationFailure("user123", "John Doe", "192.168.1.1", 
                "basic", "Invalid password");
        }

        // Then
        verify(auditService, times(3)).logAuthenticationAttempt(
            eq("user123"),
            eq("John Doe"),
            eq("basic"),
            eq("192.168.1.1"),
            eq(false),
            eq("Invalid password")
        );
        
        verify(metricsService).recordSecurityEvent("authentication_failure", "high");
    }

    @Test
    void testDetectAuthenticationFailure_ExcessiveFailures() {
        // When - Trigger 5 failures (should block)
        for (int i = 0; i < 5; i++) {
            alertingService.detectAuthenticationFailure("user123", "John Doe", "192.168.1.1", 
                "basic", "Invalid password");
        }

        // Then - Should log blocking action
        verify(auditService).logSecurityEvent(
            eq("ACTIVITY_BLOCKED"),
            eq("user123"),
            isNull(),
            eq("192.168.1.1"),
            eq(true),
            eq("Excessive authentication failures")
        );
        
        verify(metricsService).recordSecurityEvent("activity_blocked", "critical");
    }

    @Test
    void testDetectAuthorizationViolation() {
        // When
        alertingService.detectAuthorizationViolation("user123", "John Doe", 
            "/api/admin", "DELETE", "192.168.1.1", "Insufficient permissions");

        // Then
        verify(auditService).logAuthorizationDecision(
            eq("user123"),
            eq("John Doe"),
            eq("/api/admin"),
            eq("DELETE"),
            eq(false),
            eq("192.168.1.1"),
            eq("Insufficient permissions")
        );
        
        verify(metricsService).recordSecurityEvent("authorization_violation", "medium");
    }

    @Test
    void testDetectAuthorizationViolation_MultipleViolations() {
        // When - Trigger 3 violations
        for (int i = 0; i < 3; i++) {
            alertingService.detectAuthorizationViolation("user123", "John Doe", 
                "/api/admin", "DELETE", "192.168.1.1", "Insufficient permissions");
        }

        // Then - Should trigger admin notification
        verify(metricsService, times(3)).recordSecurityEvent("authorization_violation", "medium");
    }

    @Test
    void testDetectSuspiciousDataAccess() {
        // When - Access 10 times (threshold)
        for (int i = 0; i < 10; i++) {
            alertingService.detectSuspiciousDataAccess("user123", "John Doe", 
                "patient-456", "medical_records", "192.168.1.1", "Rapid access pattern");
        }

        // Then
        verify(auditService, times(10)).logPatientDataAccess(
            eq("user123"),
            eq("John Doe"),
            eq("patient-456"),
            eq("medical_records"),
            eq("READ"),
            eq("SUSPICIOUS"),
            eq("192.168.1.1"),
            eq("Rapid access pattern")
        );
        
        verify(metricsService).recordSecurityEvent("suspicious_access", "high");
    }

    @Test
    void testDetectEncryptionFailure() {
        // When
        alertingService.detectEncryptionFailure("encrypt", "patient_data", 
            "Key rotation failed");

        // Then
        verify(metricsService).recordSecurityEvent("encryption_failure", "critical");
    }

    @Test
    void testDetectAuditTampering() {
        // When
        alertingService.detectAuditTampering(100, 150, "Hash chain broken");

        // Then
        verify(metricsService).recordSecurityEvent("audit_tampering", "critical");
    }

    @Test
    void testDetectTLSViolation() {
        // When
        alertingService.detectTLSViolation("192.168.1.1", "/api/patients", 
            "Insecure connection attempt");

        // Then
        verify(metricsService).recordSecurityEvent("tls_violation", "high");
    }

    @Test
    void testResetSecurityViolationCounts() {
        // Given - Create some violations
        alertingService.detectAuthenticationFailure("user123", "John Doe", "192.168.1.1", 
            "basic", "Invalid password");
        
        String violationKey = "auth_failure_user123_192.168.1.1";
        assertEquals(1, alertingService.getSecurityViolationCount(violationKey));

        // When
        alertingService.resetSecurityViolationCounts();

        // Then
        assertEquals(0, alertingService.getSecurityViolationCount(violationKey));
    }

    @Test
    void testGetSecurityViolationCount() {
        // Given
        String violationKey = "auth_failure_user123_192.168.1.1";
        
        // When - No violations yet
        int initialCount = alertingService.getSecurityViolationCount(violationKey);
        
        // Then
        assertEquals(0, initialCount);
        
        // When - Add violations
        alertingService.detectAuthenticationFailure("user123", "John Doe", "192.168.1.1", 
            "basic", "Invalid password");
        alertingService.detectAuthenticationFailure("user123", "John Doe", "192.168.1.1", 
            "basic", "Invalid password");
        
        int updatedCount = alertingService.getSecurityViolationCount(violationKey);
        
        // Then
        assertEquals(2, updatedCount);
    }

    @Test
    void testAlertThrottling() {
        // When - Send multiple alerts rapidly
        for (int i = 0; i < 10; i++) {
            alertingService.alertTransformationFailure("ucs", "fhir", "Error", "id-" + i);
        }

        // Then - Should be throttled (max 5 per minute)
        verify(metricsService, atMost(5)).recordSecurityEvent("transformation_failure", "medium");
    }

    @Test
    void testDifferentUsersDifferentViolationCounts() {
        // When
        alertingService.detectAuthenticationFailure("user1", "User One", "192.168.1.1", 
            "basic", "Invalid password");
        alertingService.detectAuthenticationFailure("user2", "User Two", "192.168.1.2", 
            "basic", "Invalid password");

        // Then
        assertEquals(1, alertingService.getSecurityViolationCount("auth_failure_user1_192.168.1.1"));
        assertEquals(1, alertingService.getSecurityViolationCount("auth_failure_user2_192.168.1.2"));
    }
}
