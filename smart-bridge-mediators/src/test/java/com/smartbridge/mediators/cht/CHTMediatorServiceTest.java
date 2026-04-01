package com.smartbridge.mediators.cht;

import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.MediatorService;
import com.smartbridge.core.model.cht.CHTContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CHTMediatorServiceTest {

    @Mock private CHTApiClient chtApiClient;
    @Mock private com.smartbridge.core.audit.AuditLogger auditLogger;

    private CHTMediatorService mediatorService;

    @BeforeEach
    void setUp() {
        mediatorService = new CHTMediatorService(chtApiClient);

        // Inject mocked audit logger via reflection (same pattern as UCSMediatorServiceTest)
        try {
            java.lang.reflect.Field field = com.smartbridge.mediators.base.BaseMediatorService.class
                .getDeclaredField("auditLogger");
            field.setAccessible(true);
            field.set(mediatorService, auditLogger);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject audit logger", e);
        }
    }

    // ==================== Configuration ====================

    @Test
    void testGetConfiguration() {
        MediatorService.MediatorConfig config = mediatorService.getConfiguration();

        assertNotNull(config);
        assertEquals("CHT-Mediator", config.getName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("Mediator for Community Health Toolkit integration", config.getDescription());
    }

    // ==================== Request Routing ====================

    @Test
    void testProcessRequest_CreateOperation() throws Exception {
        CHTContact contact = createTestContact();
        Map<String, String> headers = Map.of("X-Operation", "CREATE");

        when(chtApiClient.createPerson(any())).thenReturn(contact);

        Object result = mediatorService.processRequest(contact, headers);

        assertNotNull(result);
        verify(chtApiClient).createPerson(any());
    }

    @Test
    void testProcessRequest_UpdateOperation() throws Exception {
        CHTContact contact = createTestContact();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "UPDATE");
        headers.put("X-Contact-Id", "cht-uuid-1");

        when(chtApiClient.updatePerson(eq("cht-uuid-1"), any())).thenReturn(contact);

        Object result = mediatorService.processRequest(contact, headers);

        assertNotNull(result);
        verify(chtApiClient).updatePerson(eq("cht-uuid-1"), any());
    }

    @Test
    void testProcessRequest_GetOperation() throws Exception {
        CHTContact contact = createTestContact();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "GET");
        headers.put("X-Contact-Id", "cht-uuid-1");

        when(chtApiClient.getContact("cht-uuid-1")).thenReturn(contact);

        Object result = mediatorService.processRequest(contact, headers);

        assertNotNull(result);
        verify(chtApiClient).getContact("cht-uuid-1");
    }

    // ==================== Request Validation ====================

    @Test
    void testValidateRequest_NullRequest() {
        Map<String, String> headers = new HashMap<>();

        MediatorException exception = assertThrows(MediatorException.class, () ->
            mediatorService.processRequest(null, headers));

        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testValidateRequest_InvalidOperation() {
        CHTContact contact = createTestContact();
        Map<String, String> headers = Map.of("X-Operation", "DELETE");

        MediatorException exception = assertThrows(MediatorException.class, () ->
            mediatorService.processRequest(contact, headers));

        assertTrue(exception.getMessage().contains("Invalid operation"));
    }

    @Test
    void testValidateRequest_UpdateWithoutContactId() {
        CHTContact contact = createTestContact();
        Map<String, String> headers = Map.of("X-Operation", "UPDATE");

        MediatorException exception = assertThrows(MediatorException.class, () ->
            mediatorService.processRequest(contact, headers));

        assertTrue(exception.getMessage().contains("Contact ID required"));
    }

    @Test
    void testValidateRequest_GetWithoutContactId() {
        CHTContact contact = createTestContact();
        Map<String, String> headers = Map.of("X-Operation", "GET");

        MediatorException exception = assertThrows(MediatorException.class, () ->
            mediatorService.processRequest(contact, headers));

        assertTrue(exception.getMessage().contains("Contact ID required"));
    }

    // ==================== Health Check ====================

    @Test
    void testHealthCheck_Success() {
        when(chtApiClient.testConnection()).thenReturn(true);

        MediatorService.HealthCheckResult result = mediatorService.performHealthCheck();

        assertNotNull(result);
        assertTrue(result.isHealthy());
        assertTrue(result.getMessage().contains("reachable"));
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    void testHealthCheck_Failure() {
        when(chtApiClient.testConnection()).thenReturn(false);

        MediatorService.HealthCheckResult result = mediatorService.performHealthCheck();

        assertNotNull(result);
        assertFalse(result.isHealthy());
    }

    @Test
    void testHealthCheck_Exception() {
        when(chtApiClient.testConnection()).thenThrow(new RuntimeException("Connection refused"));

        MediatorService.HealthCheckResult result = mediatorService.performHealthCheck();

        assertNotNull(result);
        assertFalse(result.isHealthy());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    // ==================== Authentication ====================

    @Test
    void testAuthenticate_ReturnsBasicAuth() throws MediatorException {
        String result = mediatorService.authenticate(Map.of());

        assertEquals("BASIC_AUTH", result);
    }

    // ==================== Endpoints & Channel Config ====================

    @Test
    void testGetEndpoints() {
        Map<String, String> endpoints = mediatorService.getEndpoints();

        assertNotNull(endpoints);
        assertEquals("/cht/health", endpoints.get("health"));
        assertEquals("/cht/contacts", endpoints.get("contacts"));
        assertEquals("/cht/reports", endpoints.get("reports"));
        assertEquals(3, endpoints.size());
    }

    @Test
    void testGetDefaultChannelConfig() {
        Map<String, Object> config = mediatorService.getDefaultChannelConfig();

        assertNotNull(config);
        assertEquals("CHT Channel", config.get("name"));
        assertEquals("^/cht/.*$", config.get("urlPattern"));
        assertEquals("http", config.get("type"));

        String[] allow = (String[]) config.get("allow");
        assertEquals(2, allow.length);
        assertEquals("admin", allow[0]);
        assertEquals("cht-user", allow[1]);
    }

    // ==================== Helpers ====================

    private CHTContact createTestContact() {
        CHTContact c = new CHTContact();
        c.setId("cht-uuid-1");
        c.setName("Amina Juma");
        c.setType("contact");
        c.setContactType("person");
        return c;
    }
}
