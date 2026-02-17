package com.smartbridge.config;

import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.resilience.NetworkMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Manages graceful shutdown and cleanup for all Smart Bridge services.
 * Ensures in-flight operations complete before the application terminates.
 *
 * Shutdown order:
 * 1. Stop accepting new work (change detection, message consumers)
 * 2. Wait for in-flight operations to complete
 * 3. Shut down thread pools
 * 4. Close external connections
 */
@Component
public class SmartBridgeLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(SmartBridgeLifecycleManager.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Autowired(required = false)
    private FHIRChangeDetectionService changeDetectionService;

    @Autowired(required = false)
    private NetworkMonitor networkMonitor;

    @Autowired(required = false)
    @Qualifier("transformationExecutor")
    private Executor transformationExecutor;

    @Autowired(required = false)
    @Qualifier("reverseSyncExecutor")
    private Executor reverseSyncExecutor;

    /**
     * Handles application shutdown in the correct order.
     */
    @EventListener(ContextClosedEvent.class)
    @Order(0)
    public void onShutdown() {
        logger.info("Smart Bridge shutdown initiated - beginning graceful cleanup");

        stopIncomingWork();
        shutdownThreadPools();
        closeConnections();

        logger.info("Smart Bridge shutdown complete");
    }

    /**
     * Phase 1: Stop accepting new work.
     */
    private void stopIncomingWork() {
        logger.info("Phase 1: Stopping incoming work");

        if (changeDetectionService != null) {
            try {
                changeDetectionService.disablePolling();
                logger.info("FHIR change detection stopped");
            } catch (Exception e) {
                logger.warn("Error stopping change detection: {}", e.getMessage());
            }
        }

        // MessageConsumerService is managed by Spring AMQP container lifecycle - no manual stop needed
    }

    /**
     * Phase 2: Shut down thread pools, waiting for in-flight tasks.
     */
    private void shutdownThreadPools() {
        logger.info("Phase 2: Shutting down thread pools (timeout={}s)", SHUTDOWN_TIMEOUT_SECONDS);

        shutdownExecutor("transformation", transformationExecutor);
        shutdownExecutor("reverseSync", reverseSyncExecutor);
    }

    private void shutdownExecutor(String name, Executor executor) {
        if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
            try {
                int active = taskExecutor.getActiveCount();
                if (active > 0) {
                    logger.info("Waiting for {} active {} tasks to complete", active, name);
                }
                taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
                taskExecutor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);
                taskExecutor.shutdown();
                logger.info("{} executor shut down", name);
            } catch (Exception e) {
                logger.warn("Error shutting down {} executor: {}", name, e.getMessage());
            }
        }
    }

    /**
     * Phase 3: Close external connections.
     */
    private void closeConnections() {
        logger.info("Phase 3: Closing external connections");

        if (networkMonitor != null) {
            try {
                networkMonitor.stop();
                logger.info("Network monitor shut down");
            } catch (Exception e) {
                logger.warn("Error shutting down network monitor: {}", e.getMessage());
            }
        }
    }
}
