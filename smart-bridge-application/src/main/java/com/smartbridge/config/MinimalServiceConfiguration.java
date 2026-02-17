package com.smartbridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application-level configuration for Smart Bridge.
 * Core services (MetricsService, AuditService, AlertingService) are auto-scanned
 * via their @Service/@Component annotations in smart-bridge-core.
 * Thread pools are defined in ConcurrentProcessingConfig.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class MinimalServiceConfiguration {
}
