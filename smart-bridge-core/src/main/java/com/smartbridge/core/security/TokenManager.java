package com.smartbridge.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for secure token management and rotation.
 * Manages authentication tokens with automatic rotation capabilities.
 * 
 * Requirements: 8.4 - Support secure token management and rotation
 */
@Service
public class TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);
    
    private static final int TOKEN_LENGTH = 32;
    private final SecureRandom secureRandom;
    private final Map<String, TokenInfo> tokens;
    private final EncryptionService encryptionService;

    @Value("${smartbridge.security.token.rotation-interval-hours:24}")
    private int rotationIntervalHours;

    @Value("${smartbridge.security.token.grace-period-hours:1}")
    private int gracePeriodHours;

    @Value("${smartbridge.security.token.auto-rotation-enabled:true}")
    private boolean autoRotationEnabled;

    public TokenManager(EncryptionService encryptionService) {
        this.secureRandom = new SecureRandom();
        this.tokens = new ConcurrentHashMap<>();
        this.encryptionService = encryptionService;
        logger.info("Token manager initialized");
    }

    public String generateToken(String systemId) {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        TokenInfo tokenInfo = new TokenInfo(token, Instant.now());
        tokens.put(systemId, tokenInfo);
        
        logger.info("Generated new token for system: {}", systemId);
        return token;
    }

    public boolean validateToken(String systemId, String token) {
        TokenInfo tokenInfo = tokens.get(systemId);
        
        if (tokenInfo == null) {
            logger.warn("No token found for system: {}", systemId);
            return false;
        }

        if (tokenInfo.getCurrentToken().equals(token)) {
            return true;
        }

        if (tokenInfo.getPreviousToken() != null) {
            Instant rotationTime = tokenInfo.getRotationTime();
            Instant gracePeriodEnd = rotationTime.plusSeconds(gracePeriodHours * 3600L);
            
            if (Instant.now().isBefore(gracePeriodEnd) && 
                tokenInfo.getPreviousToken().equals(token)) {
                logger.debug("Token validated using previous token within grace period for system: {}", systemId);
                return true;
            }
        }

        logger.warn("Invalid token for system: {}", systemId);
        return false;
    }

    public String rotateToken(String systemId) {
        TokenInfo currentTokenInfo = tokens.get(systemId);
        
        if (currentTokenInfo == null) {
            logger.warn("Cannot rotate token - no existing token for system: {}", systemId);
            return generateToken(systemId);
        }

        String newToken = generateToken(systemId);
        TokenInfo newTokenInfo = tokens.get(systemId);
        newTokenInfo.setPreviousToken(currentTokenInfo.getCurrentToken());
        newTokenInfo.setRotationTime(Instant.now());
        
        logger.info("Rotated token for system: {}", systemId);
        return newToken;
    }

    public String getToken(String systemId) {
        TokenInfo tokenInfo = tokens.get(systemId);
        return tokenInfo != null ? tokenInfo.getCurrentToken() : null;
    }

    public String storeEncryptedToken(String systemId, String token) {
        String encryptedToken = encryptionService.encrypt(token);
        TokenInfo tokenInfo = new TokenInfo(encryptedToken, Instant.now());
        tokenInfo.setEncrypted(true);
        tokens.put(systemId, tokenInfo);
        
        logger.info("Stored encrypted token for system: {}", systemId);
        return encryptedToken;
    }

    public String getDecryptedToken(String systemId) {
        TokenInfo tokenInfo = tokens.get(systemId);
        
        if (tokenInfo == null) {
            return null;
        }

        if (tokenInfo.isEncrypted()) {
            return encryptionService.decrypt(tokenInfo.getCurrentToken());
        }
        
        return tokenInfo.getCurrentToken();
    }

    public boolean needsRotation(String systemId) {
        TokenInfo tokenInfo = tokens.get(systemId);
        
        if (tokenInfo == null) {
            return false;
        }

        Instant rotationThreshold = Instant.now().minusSeconds(rotationIntervalHours * 3600L);
        return tokenInfo.getCreatedAt().isBefore(rotationThreshold);
    }

    public void revokeToken(String systemId) {
        tokens.remove(systemId);
        logger.info("Revoked token for system: {}", systemId);
    }

    public TokenMetadata getTokenMetadata(String systemId) {
        TokenInfo tokenInfo = tokens.get(systemId);
        
        if (tokenInfo == null) {
            return null;
        }

        return new TokenMetadata(
            systemId,
            tokenInfo.getCreatedAt(),
            tokenInfo.getRotationTime(),
            needsRotation(systemId)
        );
    }

    private static class TokenInfo {
        private String currentToken;
        private String previousToken;
        private final Instant createdAt;
        private Instant rotationTime;
        private boolean encrypted;

        public TokenInfo(String currentToken, Instant createdAt) {
            this.currentToken = currentToken;
            this.createdAt = createdAt;
            this.encrypted = false;
        }

        public String getCurrentToken() {
            return currentToken;
        }

        public String getPreviousToken() {
            return previousToken;
        }

        public void setPreviousToken(String previousToken) {
            this.previousToken = previousToken;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getRotationTime() {
            return rotationTime;
        }

        public void setRotationTime(Instant rotationTime) {
            this.rotationTime = rotationTime;
        }

        public boolean isEncrypted() {
            return encrypted;
        }

        public void setEncrypted(boolean encrypted) {
            this.encrypted = encrypted;
        }
    }

    public static class TokenMetadata {
        private final String systemId;
        private final Instant createdAt;
        private final Instant lastRotation;
        private final boolean needsRotation;

        public TokenMetadata(String systemId, Instant createdAt, Instant lastRotation, boolean needsRotation) {
            this.systemId = systemId;
            this.createdAt = createdAt;
            this.lastRotation = lastRotation;
            this.needsRotation = needsRotation;
        }

        public String getSystemId() {
            return systemId;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getLastRotation() {
            return lastRotation;
        }

        public boolean isNeedsRotation() {
            return needsRotation;
        }
    }
}
