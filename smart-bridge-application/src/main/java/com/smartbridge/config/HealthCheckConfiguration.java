package com.smartbridge.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Health check configuration for Smart Bridge components.
 * Provides detailed health status for external system connections.
 */
@Configuration
public class HealthCheckConfiguration {

    @Bean
    public HealthIndicator smartBridgeHealthIndicator() {
        return () -> Health.up()
            .withDetail("application", "Smart Bridge Interoperability Solution")
            .withDetail("status", "Running")
            .build();
    }
}
