package com.smartbridge.mediators.base;

import com.smartbridge.core.interfaces.MediatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing mediator registration with OpenHIM Core.
 * Handles automatic registration on startup and periodic heartbeat.
 */
@Service
public class MediatorRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(MediatorRegistrationService.class);

    @Autowired(required = false)
    private List<MediatorService> mediators;

    @Autowired(required = false)
    private OpenHIMClient openHIMClient;

    /**
     * Register all mediators with OpenHIM Core on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerMediatorsOnStartup() {
        if (mediators == null || mediators.isEmpty()) {
            logger.info("No mediators found to register");
            return;
        }

        if (openHIMClient == null) {
            logger.warn("OpenHIM client not configured, skipping mediator registration");
            return;
        }

        logger.info("Registering {} mediators with OpenHIM Core", mediators.size());

        for (MediatorService mediator : mediators) {
            try {
                boolean success = mediator.registerWithOpenHIM();
                
                if (success) {
                    logger.info("Successfully registered mediator: {}", 
                        mediator.getConfiguration().getName());
                } else {
                    logger.warn("Failed to register mediator: {}", 
                        mediator.getConfiguration().getName());
                }
            } catch (Exception e) {
                logger.error("Error registering mediator {}: {}", 
                    mediator.getConfiguration().getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Send periodic heartbeat to OpenHIM Core for all mediators.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void sendHeartbeat() {
        if (mediators == null || mediators.isEmpty() || openHIMClient == null) {
            return;
        }

        for (MediatorService mediator : mediators) {
            try {
                String mediatorName = mediator.getConfiguration().getName();
                boolean success = openHIMClient.sendHeartbeat(mediatorName);
                
                if (!success) {
                    logger.debug("Failed to send heartbeat for mediator: {}", mediatorName);
                }
            } catch (Exception e) {
                logger.debug("Error sending heartbeat for mediator {}: {}", 
                    mediator.getConfiguration().getName(), e.getMessage());
            }
        }
    }

    /**
     * Check OpenHIM Core connectivity.
     */
    public boolean isOpenHIMReachable() {
        if (openHIMClient == null) {
            return false;
        }
        return openHIMClient.isOpenHIMReachable();
    }
}
