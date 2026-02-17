package com.smartbridge.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EncryptionService.
 * Tests encryption and decryption of sensitive data.
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // Initialize with null key to generate a random key for testing
        encryptionService = new EncryptionService(null);
    }

    @Test
    void testEncryptAndDecrypt() {
        String plaintext = "sensitive patient data";
        
        String encrypted = encryptionService.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptNullReturnsNull() {
        String result = encryptionService.encrypt(null);
        assertNull(result);
    }

    @Test
    void testEncryptEmptyStringReturnsEmpty() {
        String result = encryptionService.encrypt("");
        assertEquals("", result);
    }

    @Test
    void testDecryptNullReturnsNull() {
        String result = encryptionService.decrypt(null);
        assertNull(result);
    }

    @Test
    void testDecryptEmptyStringReturnsEmpty() {
        String result = encryptionService.decrypt("");
        assertEquals("", result);
    }

    @Test
    void testEncryptionProducesDifferentCiphertexts() {
        String plaintext = "test data";
        
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);
        
        // Due to random IV, same plaintext should produce different ciphertexts
        assertNotEquals(encrypted1, encrypted2);
        
        // But both should decrypt to the same plaintext
        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }

    @Test
    void testEncryptSensitiveData() {
        String sensitiveData = "patient-id-12345";
        
        String encrypted = encryptionService.encryptSensitiveData(sensitiveData);
        assertNotNull(encrypted);
        assertNotEquals(sensitiveData, encrypted);
        
        String decrypted = encryptionService.decryptSensitiveData(encrypted);
        assertEquals(sensitiveData, decrypted);
    }

    @Test
    void testDecryptInvalidDataThrowsException() {
        assertThrows(EncryptionException.class, () -> {
            encryptionService.decrypt("invalid-encrypted-data");
        });
    }

    @Test
    void testEncryptLongText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("This is a long text for testing encryption. ");
        }
        
        String plaintext = longText.toString();
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);
        
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptSpecialCharacters() {
        String plaintext = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?";
        
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);
        
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptUnicodeCharacters() {
        String plaintext = "Unicode: 你好世界 مرحبا العالم";
        
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);
        
        assertEquals(plaintext, decrypted);
    }
}
