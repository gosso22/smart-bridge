package com.smartbridge.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityConfig.
 * Tests security configuration setup.
 * 
 * Note: Full security filter chain testing requires Spring Boot integration tests.
 * These tests verify basic configuration instantiation.
 */
class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @Test
    void testSecurityConfigCreation() {
        securityConfig = new SecurityConfig();
        
        assertNotNull(securityConfig);
    }

    @Test
    void testSecurityConfigIsAnnotatedCorrectly() {
        securityConfig = new SecurityConfig();
        
        // Verify the configuration class can be instantiated
        // Full security filter chain testing requires Spring context
        assertNotNull(securityConfig);
        
        // Verify class has required annotations
        assertTrue(securityConfig.getClass().isAnnotationPresent(
            org.springframework.context.annotation.Configuration.class));
        assertTrue(securityConfig.getClass().isAnnotationPresent(
            org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class));
        assertTrue(securityConfig.getClass().isAnnotationPresent(
            org.springframework.scheduling.annotation.EnableScheduling.class));
    }
}
