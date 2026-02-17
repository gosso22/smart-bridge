package com.smartbridge.core.monitoring.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Health indicator for transformation service internal health.
 * Monitors memory usage and system resources.
 */
@Component
public class TransformationHealthIndicator implements HealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(TransformationHealthIndicator.class);
    private static final double MEMORY_WARNING_THRESHOLD = 0.8; // 80%
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.9; // 90%

    @Override
    public Health health() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();
            double usageRatio = (double) used / max;
            
            String status;
            Health.Builder builder;
            
            if (usageRatio >= MEMORY_CRITICAL_THRESHOLD) {
                status = "critical";
                builder = Health.down();
                logger.warn("Transformation service memory usage critical: {}%", usageRatio * 100);
            } else if (usageRatio >= MEMORY_WARNING_THRESHOLD) {
                status = "warning";
                builder = Health.up();
                logger.warn("Transformation service memory usage high: {}%", usageRatio * 100);
            } else {
                status = "healthy";
                builder = Health.up();
                logger.debug("Transformation service health check successful");
            }
            
            return builder
                    .withDetail("service", "transformation")
                    .withDetail("memory-used-mb", used / (1024 * 1024))
                    .withDetail("memory-max-mb", max / (1024 * 1024))
                    .withDetail("memory-usage-percent", String.format("%.2f", usageRatio * 100))
                    .withDetail("status", status)
                    .build();
        } catch (Exception e) {
            logger.error("Transformation service health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("service", "transformation")
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "error")
                    .build();
        }
    }
}
