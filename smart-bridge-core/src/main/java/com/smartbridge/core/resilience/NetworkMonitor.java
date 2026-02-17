package com.smartbridge.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Network connectivity monitoring and buffering for external services.
 * Monitors service availability and provides connectivity status.
 */
public class NetworkMonitor {

    private static final Logger logger = LoggerFactory.getLogger(NetworkMonitor.class);

    private final Map<String, ServiceStatus> serviceStatuses;
    private final ScheduledExecutorService scheduler;
    private final Duration checkInterval;
    private final int timeoutMillis;
    private final AtomicBoolean running;

    /**
     * Create a network monitor with default settings.
     * Default: 30 second check interval, 5 second timeout
     */
    public NetworkMonitor() {
        this(Duration.ofSeconds(30), 5000);
    }

    /**
     * Create a network monitor with custom settings.
     *
     * @param checkInterval Interval between connectivity checks
     * @param timeoutMillis Timeout for connectivity checks in milliseconds
     */
    public NetworkMonitor(Duration checkInterval, int timeoutMillis) {
        this.checkInterval = checkInterval;
        this.timeoutMillis = timeoutMillis;
        this.serviceStatuses = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "NetworkMonitor");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);

        logger.info("Network monitor created: checkInterval={}s, timeout={}ms",
            checkInterval.getSeconds(), timeoutMillis);
    }

    /**
     * Register a service for monitoring.
     *
     * @param serviceName Name of the service
     * @param healthCheckUrl URL to check for service availability
     */
    public void registerService(String serviceName, String healthCheckUrl) {
        ServiceStatus status = new ServiceStatus(serviceName, healthCheckUrl);
        serviceStatuses.put(serviceName, status);
        logger.info("Registered service for monitoring: {} at {}", serviceName, healthCheckUrl);
    }

    /**
     * Start monitoring registered services.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting network monitor");
            
            // Initial check
            checkAllServices();
            
            // Schedule periodic checks
            scheduler.scheduleAtFixedRate(
                this::checkAllServices,
                checkInterval.getSeconds(),
                checkInterval.getSeconds(),
                TimeUnit.SECONDS
            );
        }
    }

    /**
     * Stop monitoring services.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping network monitor");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Check connectivity for all registered services.
     */
    private void checkAllServices() {
        for (ServiceStatus status : serviceStatuses.values()) {
            checkService(status);
        }
    }

    /**
     * Check connectivity for a specific service.
     */
    private void checkService(ServiceStatus status) {
        try {
            URL url = new URL(status.healthCheckUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);

            int responseCode = connection.getResponseCode();
            boolean available = responseCode >= 200 && responseCode < 300;

            if (available) {
                status.markAvailable();
                if (!status.wasAvailable) {
                    logger.info("Service {} is now AVAILABLE", status.serviceName);
                }
            } else {
                status.markUnavailable();
                logger.warn("Service {} returned non-success status: {}", status.serviceName, responseCode);
            }

            connection.disconnect();
        } catch (IOException e) {
            status.markUnavailable();
            logger.warn("Service {} connectivity check failed: {}", status.serviceName, e.getMessage());
        }
    }

    /**
     * Check if a service is currently available.
     *
     * @param serviceName Name of the service
     * @return true if service is available, false otherwise
     */
    public boolean isServiceAvailable(String serviceName) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        return status != null && status.isAvailable();
    }

    /**
     * Get the last check time for a service.
     *
     * @param serviceName Name of the service
     * @return Last check time, or null if service not registered
     */
    public Instant getLastCheckTime(String serviceName) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        return status != null ? status.getLastCheckTime() : null;
    }

    /**
     * Get the last available time for a service.
     *
     * @param serviceName Name of the service
     * @return Last available time, or null if never available
     */
    public Instant getLastAvailableTime(String serviceName) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        return status != null ? status.getLastAvailableTime() : null;
    }

    /**
     * Get status for all monitored services.
     */
    public Map<String, Boolean> getAllServiceStatuses() {
        Map<String, Boolean> statuses = new ConcurrentHashMap<>();
        for (Map.Entry<String, ServiceStatus> entry : serviceStatuses.entrySet()) {
            statuses.put(entry.getKey(), entry.getValue().isAvailable());
        }
        return statuses;
    }

    /**
     * Manually trigger a connectivity check for a specific service.
     *
     * @param serviceName Name of the service
     * @return true if service is available, false otherwise
     */
    public boolean checkServiceNow(String serviceName) {
        ServiceStatus status = serviceStatuses.get(serviceName);
        if (status != null) {
            checkService(status);
            return status.isAvailable();
        }
        return false;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Internal class to track service status.
     */
    private static class ServiceStatus {
        private final String serviceName;
        private final String healthCheckUrl;
        private final AtomicBoolean available;
        private final AtomicReference<Instant> lastCheckTime;
        private final AtomicReference<Instant> lastAvailableTime;
        private volatile boolean wasAvailable;

        public ServiceStatus(String serviceName, String healthCheckUrl) {
            this.serviceName = serviceName;
            this.healthCheckUrl = healthCheckUrl;
            this.available = new AtomicBoolean(false);
            this.lastCheckTime = new AtomicReference<>();
            this.lastAvailableTime = new AtomicReference<>();
            this.wasAvailable = false;
        }

        public void markAvailable() {
            wasAvailable = available.get();
            available.set(true);
            Instant now = Instant.now();
            lastCheckTime.set(now);
            lastAvailableTime.set(now);
        }

        public void markUnavailable() {
            wasAvailable = available.get();
            available.set(false);
            lastCheckTime.set(Instant.now());
        }

        public boolean isAvailable() {
            return available.get();
        }

        public Instant getLastCheckTime() {
            return lastCheckTime.get();
        }

        public Instant getLastAvailableTime() {
            return lastAvailableTime.get();
        }
    }
}
