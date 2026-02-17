package com.smartbridge.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TLSConfig.
 * Tests TLS configuration for secure communications.
 */
class TLSConfigTest {

    private TLSConfig tlsConfig;
    private RestTemplateBuilder restTemplateBuilder;

    @BeforeEach
    void setUp() {
        tlsConfig = new TLSConfig();
        restTemplateBuilder = new RestTemplateBuilder();
        
        // Set test values
        ReflectionTestUtils.setField(tlsConfig, "tlsEnabled", false);
        ReflectionTestUtils.setField(tlsConfig, "tlsProtocol", "TLSv1.3");
        ReflectionTestUtils.setField(tlsConfig, "verifyHostname", true);
    }

    @Test
    void testSecureRestTemplateCreationWhenTlsDisabled() {
        RestTemplate restTemplate = tlsConfig.secureRestTemplate(restTemplateBuilder);
        
        assertNotNull(restTemplate);
    }

    @Test
    void testSecureRestTemplateCreationWhenTlsEnabled() {
        ReflectionTestUtils.setField(tlsConfig, "tlsEnabled", true);
        
        // Without keystore/truststore paths, should create RestTemplate with default SSL context
        // The implementation handles missing keystore/truststore gracefully
        RestTemplate restTemplate = tlsConfig.secureRestTemplate(restTemplateBuilder);
        
        assertNotNull(restTemplate);
    }
}
