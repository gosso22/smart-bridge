package com.smartbridge.mediators.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for mediator services.
 * Enables scheduling for heartbeat and health check operations.
 */
@Configuration
@EnableScheduling
public class MediatorConfig {
}
