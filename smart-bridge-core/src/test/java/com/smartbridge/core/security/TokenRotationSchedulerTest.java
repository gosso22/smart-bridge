package com.smartbridge.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenRotationScheduler.
 * Tests automatic token rotation scheduling.
 */
class TokenRotationSchedulerTest {

    private TokenRotationScheduler scheduler;
    private TokenManager tokenManager;
    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(null);
        tokenManager = new TokenManager(encryptionService);
        scheduler = new TokenRotationScheduler(tokenManager);
        
        // Set test values
        ReflectionTestUtils.setField(scheduler, "autoRotationEnabled", true);
        ReflectionTestUtils.setField(scheduler, "monitoredSystems", 
            Arrays.asList("ucs-system", "fhir-system"));
    }

    @Test
    void testCheckAndRotateTokensWhenDisabled() {
        ReflectionTestUtils.setField(scheduler, "autoRotationEnabled", false);
        
        // Should not throw exception
        scheduler.checkAndRotateTokens();
    }

    @Test
    void testCheckAndRotateTokensWithNoTokens() {
        // Should not throw exception even with no tokens
        scheduler.checkAndRotateTokens();
    }

    @Test
    void testManualRotation() {
        String systemId = "ucs-system";
        tokenManager.generateToken(systemId);
        
        String newToken = scheduler.manualRotation(systemId);
        
        assertNotNull(newToken);
        assertTrue(tokenManager.validateToken(systemId, newToken));
    }

    @Test
    void testGetTokenStatus() {
        String systemId = "ucs-system";
        tokenManager.generateToken(systemId);
        
        TokenManager.TokenMetadata metadata = scheduler.getTokenStatus(systemId);
        
        assertNotNull(metadata);
        assertEquals(systemId, metadata.getSystemId());
    }

    @Test
    void testGetTokenStatusForNonExistentSystem() {
        TokenManager.TokenMetadata metadata = scheduler.getTokenStatus("non-existent");
        
        assertNull(metadata);
    }
}
