package com.smartbridge.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenManager.
 * Tests token generation, validation, rotation, and encryption.
 */
class TokenManagerTest {

    private TokenManager tokenManager;
    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(null);
        tokenManager = new TokenManager(encryptionService);
        
        // Set test values
        ReflectionTestUtils.setField(tokenManager, "rotationIntervalHours", 24);
        ReflectionTestUtils.setField(tokenManager, "gracePeriodHours", 1);
        ReflectionTestUtils.setField(tokenManager, "autoRotationEnabled", true);
    }

    @Test
    void testGenerateToken() {
        String systemId = "ucs-system";
        
        String token = tokenManager.generateToken(systemId);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGenerateTokenProducesDifferentTokens() {
        String systemId1 = "ucs-system";
        String systemId2 = "fhir-system";
        
        String token1 = tokenManager.generateToken(systemId1);
        String token2 = tokenManager.generateToken(systemId2);
        
        assertNotEquals(token1, token2);
    }

    @Test
    void testValidateTokenSuccess() {
        String systemId = "ucs-system";
        String token = tokenManager.generateToken(systemId);
        
        boolean isValid = tokenManager.validateToken(systemId, token);
        
        assertTrue(isValid);
    }

    @Test
    void testValidateTokenFailureWithWrongToken() {
        String systemId = "ucs-system";
        tokenManager.generateToken(systemId);
        
        boolean isValid = tokenManager.validateToken(systemId, "wrong-token");
        
        assertFalse(isValid);
    }

    @Test
    void testValidateTokenFailureWithNonExistentSystem() {
        boolean isValid = tokenManager.validateToken("non-existent-system", "any-token");
        
        assertFalse(isValid);
    }

    @Test
    void testRotateToken() {
        String systemId = "ucs-system";
        String originalToken = tokenManager.generateToken(systemId);
        
        String newToken = tokenManager.rotateToken(systemId);
        
        assertNotNull(newToken);
        assertNotEquals(originalToken, newToken);
        assertTrue(tokenManager.validateToken(systemId, newToken));
    }

    @Test
    void testRotateTokenAllowsPreviousTokenDuringGracePeriod() {
        String systemId = "ucs-system";
        String originalToken = tokenManager.generateToken(systemId);
        
        String newToken = tokenManager.rotateToken(systemId);
        
        // Both tokens should be valid during grace period
        assertTrue(tokenManager.validateToken(systemId, newToken));
        assertTrue(tokenManager.validateToken(systemId, originalToken));
    }

    @Test
    void testGetToken() {
        String systemId = "ucs-system";
        String token = tokenManager.generateToken(systemId);
        
        String retrievedToken = tokenManager.getToken(systemId);
        
        assertEquals(token, retrievedToken);
    }

    @Test
    void testGetTokenReturnsNullForNonExistentSystem() {
        String token = tokenManager.getToken("non-existent-system");
        
        assertNull(token);
    }

    @Test
    void testStoreEncryptedToken() {
        String systemId = "ucs-system";
        String plainToken = "my-secret-token";
        
        String encryptedToken = tokenManager.storeEncryptedToken(systemId, plainToken);
        
        assertNotNull(encryptedToken);
        assertNotEquals(plainToken, encryptedToken);
    }

    @Test
    void testGetDecryptedToken() {
        String systemId = "ucs-system";
        String plainToken = "my-secret-token";
        
        tokenManager.storeEncryptedToken(systemId, plainToken);
        String decryptedToken = tokenManager.getDecryptedToken(systemId);
        
        assertEquals(plainToken, decryptedToken);
    }

    @Test
    void testGetDecryptedTokenForUnencryptedToken() {
        String systemId = "ucs-system";
        String token = tokenManager.generateToken(systemId);
        
        String retrievedToken = tokenManager.getDecryptedToken(systemId);
        
        assertEquals(token, retrievedToken);
    }

    @Test
    void testGetDecryptedTokenReturnsNullForNonExistentSystem() {
        String token = tokenManager.getDecryptedToken("non-existent-system");
        
        assertNull(token);
    }

    @Test
    void testNeedsRotation() {
        String systemId = "ucs-system";
        tokenManager.generateToken(systemId);
        
        // Newly generated token should not need rotation
        assertFalse(tokenManager.needsRotation(systemId));
    }

    @Test
    void testNeedsRotationReturnsFalseForNonExistentSystem() {
        assertFalse(tokenManager.needsRotation("non-existent-system"));
    }

    @Test
    void testRevokeToken() {
        String systemId = "ucs-system";
        String token = tokenManager.generateToken(systemId);
        
        tokenManager.revokeToken(systemId);
        
        assertNull(tokenManager.getToken(systemId));
        assertFalse(tokenManager.validateToken(systemId, token));
    }

    @Test
    void testGetTokenMetadata() {
        String systemId = "ucs-system";
        tokenManager.generateToken(systemId);
        
        TokenManager.TokenMetadata metadata = tokenManager.getTokenMetadata(systemId);
        
        assertNotNull(metadata);
        assertEquals(systemId, metadata.getSystemId());
        assertNotNull(metadata.getCreatedAt());
        assertFalse(metadata.isNeedsRotation());
    }

    @Test
    void testGetTokenMetadataReturnsNullForNonExistentSystem() {
        TokenManager.TokenMetadata metadata = tokenManager.getTokenMetadata("non-existent-system");
        
        assertNull(metadata);
    }

    @Test
    void testGetTokenMetadataAfterRotation() {
        String systemId = "ucs-system";
        tokenManager.generateToken(systemId);
        tokenManager.rotateToken(systemId);
        
        TokenManager.TokenMetadata metadata = tokenManager.getTokenMetadata(systemId);
        
        assertNotNull(metadata);
        assertNotNull(metadata.getLastRotation());
    }

    @Test
    void testRotateTokenForNonExistentSystemGeneratesNewToken() {
        String systemId = "new-system";
        
        String token = tokenManager.rotateToken(systemId);
        
        assertNotNull(token);
        assertTrue(tokenManager.validateToken(systemId, token));
    }
}
