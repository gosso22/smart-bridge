package com.smartbridge.core.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AuditService auditService;

    @Test
    void testLogPatientDataAccess_CreatesAuditEntry() {
        // Arrange
        String userId = "user123";
        String userName = "Dr. Smith";
        String patientId = "patient456";
        String dataType = "Patient";
        String operation = "READ";
        String sourceSystem = "UCS";
        String sourceIp = "192.168.1.100";
        String details = "Accessed patient demographics";

        // Act
        auditService.logPatientDataAccess(userId, userName, patientId, dataType, 
            operation, sourceSystem, sourceIp, details);

        // Assert
        verify(auditLogger).logDataAccess(
            eq(userId),
            eq(patientId),
            eq(dataType),
            eq(operation),
            eq(sourceSystem),
            contains("SeqNum=1")
        );
        assertEquals(1, auditService.getCurrentSequenceNumber());
    }

    @Test
    void testLogTransformation_CreatesAuditEntryWithHash() {
        // Arrange
        String userId = "user123";
        String sourceSystem = "UCS";
        String targetSystem = "FHIR";
        String operation = "TRANSFORM";
        String sourceId = "ucs-123";
        String targetId = "fhir-456";

        // Act
        auditService.logTransformation(userId, sourceSystem, targetSystem, 
            operation, sourceId, targetId, true, "Successful transformation");

        // Assert
        verify(auditLogger).logTransformation(
            eq(sourceSystem),
            eq(targetSystem),
            eq(operation),
            eq(sourceId),
            eq(targetId),
            eq(true),
            contains("Hash=")
        );
        assertNotNull(auditService.getHashForSequence(1));
    }

    @Test
    void testLogMediatorOperation_IncrementsSequenceNumber() {
        // Arrange
        String userId = "user123";
        String mediatorName = "UCSMediator";
        String operation = "ROUTE";
        String requestId = "req-789";

        // Act
        auditService.logMediatorOperation(userId, mediatorName, operation, 
            requestId, true, 150L, "Routed successfully");

        // Assert
        verify(auditLogger).logMediatorOperation(
            eq(mediatorName),
            eq(operation),
            eq(requestId),
            eq(true),
            eq(150L),
            contains("UserId=" + userId)
        );
        assertEquals(1, auditService.getCurrentSequenceNumber());
    }

    @Test
    void testLogFHIROperation_WithUserContext() {
        // Arrange
        String userId = "user123";
        String operation = "CREATE";
        String resourceType = "Patient";
        String resourceId = "patient-123";
        String fhirServer = "http://fhir.example.com";

        // Act
        auditService.logFHIROperation(userId, operation, resourceType, 
            resourceId, fhirServer, true, "Created patient resource");

        // Assert
        verify(auditLogger).logFHIROperation(
            eq(operation),
            eq(resourceType),
            eq(resourceId),
            eq(fhirServer),
            eq(true),
            contains("UserId=" + userId)
        );
    }

    @Test
    void testLogSecurityEvent_WithUserIdentification() {
        // Arrange
        String eventType = "LOGIN_ATTEMPT";
        String userId = "user123";
        String userName = "Dr. Smith";
        String sourceIp = "192.168.1.100";

        // Act
        auditService.logSecurityEvent(eventType, userId, userName, sourceIp, true, "Successful login");

        // Assert
        verify(auditLogger).logSecurityEvent(
            eq(eventType),
            eq(userId),
            eq(sourceIp),
            eq(true),
            contains("UserName=" + userName)
        );
    }

    @Test
    void testLogAuthenticationAttempt_CreatesSecurityEvent() {
        // Arrange
        String userId = "user123";
        String userName = "Dr. Smith";
        String authMethod = "Bearer Token";
        String sourceIp = "192.168.1.100";

        // Act
        auditService.logAuthenticationAttempt(userId, userName, authMethod, 
            sourceIp, true, "Token validated");

        // Assert
        verify(auditLogger).logSecurityEvent(
            eq("AUTHENTICATION"),
            eq(userId),
            eq(sourceIp),
            eq(true),
            contains("Method=" + authMethod)
        );
    }

    @Test
    void testLogAuthorizationDecision_WithResourceAndAction() {
        // Arrange
        String userId = "user123";
        String userName = "Dr. Smith";
        String resource = "Patient/123";
        String action = "READ";
        String sourceIp = "192.168.1.100";

        // Act
        auditService.logAuthorizationDecision(userId, userName, resource, 
            action, true, sourceIp, "Access granted");

        // Assert
        verify(auditLogger).logSecurityEvent(
            eq("AUTHORIZATION"),
            eq(userId),
            eq(sourceIp),
            eq(true),
            argThat(details -> 
                details.contains("Resource=" + resource) &&
                details.contains("Action=" + action) &&
                details.contains("Granted=true")
            )
        );
    }

    @Test
    void testAuditChainIntegrity_MultipleEntries() {
        // Arrange & Act
        auditService.logPatientDataAccess("user1", "User One", "patient1", 
            "Patient", "READ", "UCS", "192.168.1.1", "First access");
        auditService.logPatientDataAccess("user2", "User Two", "patient2", 
            "Patient", "READ", "UCS", "192.168.1.2", "Second access");
        auditService.logPatientDataAccess("user3", "User Three", "patient3", 
            "Patient", "READ", "UCS", "192.168.1.3", "Third access");

        // Assert
        assertEquals(3, auditService.getCurrentSequenceNumber());
        assertNotNull(auditService.getHashForSequence(1));
        assertNotNull(auditService.getHashForSequence(2));
        assertNotNull(auditService.getHashForSequence(3));
        
        // Verify each hash is different
        String hash1 = auditService.getHashForSequence(1);
        String hash2 = auditService.getHashForSequence(2);
        String hash3 = auditService.getHashForSequence(3);
        assertNotEquals(hash1, hash2);
        assertNotEquals(hash2, hash3);
        assertNotEquals(hash1, hash3);
    }

    @Test
    void testVerifyAuditIntegrity_ValidChain() {
        // Arrange
        auditService.logPatientDataAccess("user1", "User One", "patient1", 
            "Patient", "READ", "UCS", "192.168.1.1", "Access 1");
        auditService.logPatientDataAccess("user2", "User Two", "patient2", 
            "Patient", "READ", "UCS", "192.168.1.2", "Access 2");
        auditService.logPatientDataAccess("user3", "User Three", "patient3", 
            "Patient", "READ", "UCS", "192.168.1.3", "Access 3");

        // Act
        boolean isValid = auditService.verifyAuditIntegrity(1, 3);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testVerifyAuditIntegrity_InvalidRange() {
        // Arrange
        auditService.logPatientDataAccess("user1", "User One", "patient1", 
            "Patient", "READ", "UCS", "192.168.1.1", "Access 1");

        // Act
        boolean isValid = auditService.verifyAuditIntegrity(5, 10);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testSequenceNumberIncrementsCorrectly() {
        // Arrange & Act
        assertEquals(0, auditService.getCurrentSequenceNumber());
        
        auditService.logPatientDataAccess("user1", "User One", "patient1", 
            "Patient", "READ", "UCS", "192.168.1.1", "Access 1");
        assertEquals(1, auditService.getCurrentSequenceNumber());
        
        auditService.logTransformation("user1", "UCS", "FHIR", "TRANSFORM", 
            "ucs-1", "fhir-1", true, "Transform 1");
        assertEquals(2, auditService.getCurrentSequenceNumber());
        
        auditService.logSecurityEvent("LOGIN", "user1", "User One", 
            "192.168.1.1", true, "Login 1");
        assertEquals(3, auditService.getCurrentSequenceNumber());
    }

    @Test
    void testHashChainLinking() {
        // Arrange & Act
        auditService.logPatientDataAccess("user1", "User One", "patient1", 
            "Patient", "READ", "UCS", "192.168.1.1", "Access 1");
        String hash1 = auditService.getHashForSequence(1);
        
        auditService.logPatientDataAccess("user2", "User Two", "patient2", 
            "Patient", "READ", "UCS", "192.168.1.2", "Access 2");
        String hash2 = auditService.getHashForSequence(2);

        // Assert - hashes should be different and non-null
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2);
        assertTrue(hash1.length() > 0);
        assertTrue(hash2.length() > 0);
    }
}
