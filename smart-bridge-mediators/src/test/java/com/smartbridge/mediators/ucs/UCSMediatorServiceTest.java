package com.smartbridge.mediators.ucs;

import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.MediatorService;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.validation.UCSClientValidator;
import com.smartbridge.core.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UCSMediatorServiceTest {

    @Mock
    private UCSClientValidator validator;
    
    @Mock
    private com.smartbridge.core.audit.AuditLogger auditLogger;

    private UCSMediatorService mediatorService;

    @BeforeEach
    void setUp() {
        mediatorService = new UCSMediatorService(
            "http://localhost:9999/ucs",  // Use non-existent port for tests
            "BASIC",
            "testuser",
            "testpass",
            "",
            validator
        );
        
        // Inject the mocked audit logger using reflection
        try {
            java.lang.reflect.Field field = com.smartbridge.mediators.base.BaseMediatorService.class
                .getDeclaredField("auditLogger");
            field.setAccessible(true);
            field.set(mediatorService, auditLogger);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject audit logger", e);
        }
    }

    @Test
    void testGetConfiguration() {
        MediatorService.MediatorConfig config = mediatorService.getConfiguration();
        
        assertNotNull(config);
        assertEquals("UCS-Mediator", config.getName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("Mediator for UCS legacy system integration", config.getDescription());
    }

    @Test
    void testValidateRequest_NullRequest() {
        Map<String, String> headers = new HashMap<>();
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(null, headers);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testValidateRequest_InvalidOperation() {
        UCSClient client = createTestClient();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "INVALID");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(client, headers);
        });
        
        assertTrue(exception.getMessage().contains("Invalid operation"));
    }

    @Test
    void testValidateRequest_UpdateWithoutClientId() {
        UCSClient client = createTestClient();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "UPDATE");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(client, headers);
        });
        
        assertTrue(exception.getMessage().contains("Client ID required"));
    }

    @Test
    void testValidateRequest_GetWithoutClientId() {
        UCSClient client = createTestClient();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "GET");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(client, headers);
        });
        
        assertTrue(exception.getMessage().contains("Client ID required"));
    }

    @Test
    void testValidateRequest_ValidationFailure() throws ValidationException {
        UCSClient client = createTestClient();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "CREATE");
        
        doThrow(new RuntimeException("Invalid client data"))
            .when(validator).validate(any(UCSClient.class));
        
        // The mediator wraps the RuntimeException in a MediatorException
        assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(client, headers);
        });
        
        verify(validator).validate(any(UCSClient.class));
    }

    @Test
    void testHealthCheck() {
        MediatorService.HealthCheckResult result = mediatorService.performHealthCheck();
        
        assertNotNull(result);
        // Health check will fail since we're using non-existent port 9999
        assertFalse(result.isHealthy());
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    void testGetEndpoints() {
        Map<String, String> endpoints = mediatorService.getEndpoints();
        
        assertNotNull(endpoints);
        assertTrue(endpoints.containsKey("health"));
        assertTrue(endpoints.containsKey("clients"));
        assertTrue(endpoints.containsKey("authenticate"));
        assertEquals("/ucs/health", endpoints.get("health"));
        assertEquals("/ucs/clients", endpoints.get("clients"));
    }

    @Test
    void testGetDefaultChannelConfig() {
        Map<String, Object> config = mediatorService.getDefaultChannelConfig();
        
        assertNotNull(config);
        assertEquals("UCS Channel", config.get("name"));
        assertEquals("^/ucs/.*$", config.get("urlPattern"));
        assertEquals("http", config.get("type"));
    }

    private UCSClient createTestClient() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("test-entity-001");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "opensrp-123");
        client.setIdentifiers(identifiers);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("national_id", "national-456");
        client.setAttributes(attributes);

        client.setFirstName("John");
        client.setLastName("Doe");
        client.setGender("M");
        client.setBirthdate("1990-01-01");

        return client;
    }
}
