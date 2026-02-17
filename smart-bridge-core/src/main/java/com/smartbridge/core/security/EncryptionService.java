package com.smartbridge.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data at rest.
 * Uses AES-256-GCM for authenticated encryption.
 * 
 * Requirements: 8.2 - Implement encryption at rest for sensitive data storage
 */
@Service
public class EncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionService(
            @Value("${smartbridge.security.encryption.key:#{null}}") String base64Key) {
        this.secureRandom = new SecureRandom();
        
        if (base64Key != null && !base64Key.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            logger.info("Encryption service initialized with provided key");
        } else {
            this.secretKey = generateKey();
            logger.warn("Encryption service initialized with generated key - this should only be used for development");
        }
    }

    /**
     * Generates a new AES-256 encryption key.
     * 
     * @return Generated SecretKey
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, secureRandom);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityConfigurationException("Failed to generate encryption key", e);
        }
    }

    /**
     * Encrypts sensitive data using AES-256-GCM.
     * 
     * @param plaintext The data to encrypt
     * @return Base64-encoded encrypted data with IV prepended
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt data
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts data that was encrypted with the encrypt method.
     * 
     * @param encryptedData Base64-encoded encrypted data with IV prepended
     * @return Decrypted plaintext
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            // Decode from Base64
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt data
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Encrypts sensitive fields in patient data.
     * This method can be extended to handle specific data structures.
     * 
     * @param sensitiveData The sensitive data to encrypt
     * @return Encrypted data
     */
    public String encryptSensitiveData(String sensitiveData) {
        logger.debug("Encrypting sensitive data");
        return encrypt(sensitiveData);
    }

    /**
     * Decrypts sensitive fields in patient data.
     * 
     * @param encryptedData The encrypted data to decrypt
     * @return Decrypted data
     */
    public String decryptSensitiveData(String encryptedData) {
        logger.debug("Decrypting sensitive data");
        return decrypt(encryptedData);
    }

    /**
     * Gets the Base64-encoded encryption key for configuration purposes.
     * WARNING: This should only be used for initial setup and key rotation.
     * 
     * @return Base64-encoded key
     */
    public String getEncodedKey() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
}
