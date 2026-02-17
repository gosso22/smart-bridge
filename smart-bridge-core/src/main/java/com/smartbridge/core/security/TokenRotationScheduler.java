package com.smartbridge.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task for automatic token rotation.
 * Periodically checks and rotates tokens that have exceeded their rotation interval.
 * 
 * Requirements: 8.4 - Support secure token management and rotation
 */
@Component
public class TokenRotationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TokenRotationScheduler.class);

    private final TokenManager tokenManager;

    @Value("${smartbridge.security.token.auto-rotation-enabled:true}")
    private boolean autoRotationEnabled;

    @Value("${smartbridge.security.token.monitored-systems:ucs-system,fhir-system,gothomis-system}")
    private List<String> monitoredSystems;

    public TokenRotationScheduler(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    /**
     * Checks and rotates tokens that need rotation.
     * Runs every hour by default.
     */
    @Scheduled(cron = "${smartbridge.security.token.rotation-check-cron:0 0 * * * *}")
    public void checkAndRotateTokens() {
        if (!autoRotationEnabled) {
            logger.debug("Automatic token rotation is disabled");
            return;
        }

        logger.info("Starting scheduled token rotation check");
        int rotatedCount = 0;

        for (String systemId : monitoredSystems) {
            try {
                if (tokenManager.needsRotation(systemId)) {
                    logger.info("Rotating token for system: {}", systemId);
                    tokenManager.rotateToken(systemId);
                    rotatedCount++;
                }
            } catch (Exception e) {
                logger.error("Failed to rotate token for system: {}", systemId, e);
            }
        }

        logger.info("Completed token rotation check. Rotated {} tokens", rotatedCount);
    }

    /**
     * Manually triggers token rotation for a specific system.
     * 
     * @param systemId The system ID to rotate token for
     * @return The new token
     */
    public String manualRotation(String systemId) {
        logger.info("Manual token rotation requested for system: {}", systemId);
        return tokenManager.rotateToken(systemId);
    }

    /**
     * Gets token metadata for monitoring purposes.
     * 
     * @param systemId The system ID to get metadata for
     * @return Token metadata
     */
    public TokenManager.TokenMetadata getTokenStatus(String systemId) {
        return tokenManager.getTokenMetadata(systemId);
    }
}
