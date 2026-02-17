package com.smartbridge.mediators.base;

import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.MediatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BaseMediatorServiceTest {

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private OpenHIMClient openHIMClient;

    private TestMediatorService mediator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        MediatorService.MediatorConfig config = new MediatorService.MediatorConfig(
            "test-mediator",
            "1.0.0",
            "Test mediator for unit testing",
            new HashMap<>()
        );
        
        mediator = new TestMediatorService(config);
        mediator.auditLogger = auditLogger;
        mediator.openHIMClient = openHIMClient;
    }

    @Test
    void testProcessRequest_Success() throws MediatorException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        String request = "test request";
        String response = (String) mediator.processRequest(request, headers);
        
        assertEquals("processed: test request", response);
        
        verify(auditLogger).logMediatorOperation(
            eq("test-mediator"),
            eq("PROCESS_REQUEST"),
            anyString(),
            eq(true),
            anyLong(),
            eq("Request processed successfully")
        );
    }

    @Test
    void testProcessRequest_NullRequest() {
        Map<String, String> headers = new HashMap<>();
        
        MediatorException exception = assertThrows(
            MediatorException.class,
            () -> mediator.processRequest(null, headers)
        );
        
        assertTrue(exception.getMessage().contains("Request cannot be null"));
        assertEquals(400, exception.getHttpStatusCode());
    }

    @Test
    void testProcessRequest_MediatorException() {
        Map<String, String> headers = new HashMap<>();
        String request = "error";
        
        MediatorException exception = assertThrows(
            MediatorException.class,
            () -> mediator.processRequest(request, headers)
        );
        
        assertTrue(exception.getMessage().contains("Processing failed"));
        
        verify(auditLogger).logMediatorOperation(
            eq("test-mediator"),
            eq("PROCESS_REQUEST"),
            anyString(),
            eq(false),
            anyLong(),
            contains("Processing failed")
        );
    }

    @Test
    void testRegisterWithOpenHIM_Success() {
        when(openHIMClient.registerMediator(any(MediatorRegistration.class))).thenReturn(true);
        
        boolean result = mediator.registerWithOpenHIM();
        
        assertTrue(result);
        assertTrue(mediator.isRegistered());
        
        verify(openHIMClient).registerMediator(any(MediatorRegistration.class));
        verify(auditLogger).logMediatorOperation(
            eq("test-mediator"),
            eq("REGISTRATION"),
            anyString(),
            eq(true),
            eq(0L),
            eq("Mediator registered with OpenHIM")
        );
    }

    @Test
    void testRegisterWithOpenHIM_Failure() {
        when(openHIMClient.registerMediator(any(MediatorRegistration.class))).thenReturn(false);
        
        boolean result = mediator.registerWithOpenHIM();
        
        assertFalse(result);
        assertFalse(mediator.isRegistered());
        
        verify(openHIMClient).registerMediator(any(MediatorRegistration.class));
    }

    @Test
    void testRegisterWithOpenHIM_NoClient() {
        mediator.openHIMClient = null;
        
        boolean result = mediator.registerWithOpenHIM();
        
        assertFalse(result);
        assertFalse(mediator.isRegistered());
    }

    @Test
    void testPerformHealthCheck_Healthy() {
        MediatorService.HealthCheckResult result = mediator.performHealthCheck();
        
        assertTrue(result.isHealthy());
        assertEquals("Test mediator is healthy", result.getMessage());
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    void testAuthenticate_Success() throws MediatorException {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "testuser");
        credentials.put("password", "testpass");
        
        String token = mediator.authenticate(credentials);
        
        assertEquals("test-token", token);
        
        verify(auditLogger).logSecurityEvent(
            eq("AUTHENTICATION"),
            eq("testuser"),
            eq("unknown"),
            eq(true),
            contains("Authentication successful")
        );
    }

    @Test
    void testAuthenticate_Failure() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "invalid");
        
        MediatorException exception = assertThrows(
            MediatorException.class,
            () -> mediator.authenticate(credentials)
        );
        
        assertTrue(exception.getMessage().contains("Authentication failed"));
        
        verify(auditLogger).logSecurityEvent(
            eq("AUTHENTICATION"),
            eq("invalid"),
            eq("unknown"),
            eq(false),
            contains("Authentication failed")
        );
    }

    @Test
    void testGetConfiguration() {
        MediatorService.MediatorConfig config = mediator.getConfiguration();
        
        assertNotNull(config);
        assertEquals("test-mediator", config.getName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("Test mediator for unit testing", config.getDescription());
    }

    /**
     * Test implementation of BaseMediatorService for testing purposes.
     */
    private static class TestMediatorService extends BaseMediatorService {

        public TestMediatorService(MediatorConfig config) {
            super(config);
        }

        @Override
        protected Object doProcessRequest(Object request, Map<String, String> headers, String requestId) 
                throws MediatorException {
            if ("error".equals(request)) {
                throw new MediatorException(
                    "Processing failed",
                    getConfiguration().getName(),
                    "PROCESS_REQUEST",
                    500
                );
            }
            return "processed: " + request;
        }

        @Override
        protected HealthCheckResult doHealthCheck() {
            return new HealthCheckResult(true, "Test mediator is healthy", 10);
        }

        @Override
        protected String doAuthenticate(Map<String, String> credentials) throws MediatorException {
            String username = credentials.get("username");
            if ("invalid".equals(username)) {
                throw new MediatorException(
                    "Authentication failed",
                    getConfiguration().getName(),
                    "AUTHENTICATE",
                    401
                );
            }
            return "test-token";
        }
    }
}
