package com.smartbridge.mediators.base;

import com.smartbridge.core.interfaces.MediatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediatorHealthControllerTest {

    private MediatorHealthController controller;
    private MediatorService mockMediator1;
    private MediatorService mockMediator2;

    @BeforeEach
    void setUp() {
        controller = new MediatorHealthController();
        
        mockMediator1 = mock(MediatorService.class);
        mockMediator2 = mock(MediatorService.class);
        
        MediatorService.MediatorConfig config1 = new MediatorService.MediatorConfig(
            "mediator-1",
            "1.0.0",
            "First test mediator",
            new HashMap<>()
        );
        
        MediatorService.MediatorConfig config2 = new MediatorService.MediatorConfig(
            "mediator-2",
            "1.0.0",
            "Second test mediator",
            new HashMap<>()
        );
        
        when(mockMediator1.getConfiguration()).thenReturn(config1);
        when(mockMediator2.getConfiguration()).thenReturn(config2);
        
        List<MediatorService> mediators = Arrays.asList(mockMediator1, mockMediator2);
        ReflectionTestUtils.setField(controller, "mediators", mediators);
    }

    @Test
    void testHealthCheck_AllHealthy() {
        when(mockMediator1.performHealthCheck())
            .thenReturn(new MediatorService.HealthCheckResult(true, "Healthy", 10));
        when(mockMediator2.performHealthCheck())
            .thenReturn(new MediatorService.HealthCheckResult(true, "Healthy", 15));

        ResponseEntity<Map<String, Object>> response = controller.healthCheck();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mediators = (Map<String, Object>) body.get("mediators");
        assertNotNull(mediators);
        assertEquals(2, mediators.size());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mediator1Status = (Map<String, Object>) mediators.get("mediator-1");
        assertTrue((Boolean) mediator1Status.get("healthy"));
        assertEquals("Healthy", mediator1Status.get("message"));
    }

    @Test
    void testHealthCheck_OneUnhealthy() {
        when(mockMediator1.performHealthCheck())
            .thenReturn(new MediatorService.HealthCheckResult(true, "Healthy", 10));
        when(mockMediator2.performHealthCheck())
            .thenReturn(new MediatorService.HealthCheckResult(false, "Connection failed", 20));

        ResponseEntity<Map<String, Object>> response = controller.healthCheck();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("DOWN", body.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mediators = (Map<String, Object>) body.get("mediators");
        assertNotNull(mediators);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mediator2Status = (Map<String, Object>) mediators.get("mediator-2");
        assertFalse((Boolean) mediator2Status.get("healthy"));
        assertEquals("Connection failed", mediator2Status.get("message"));
    }

    @Test
    void testHealthCheck_NoMediators() {
        ReflectionTestUtils.setField(controller, "mediators", null);

        ResponseEntity<Map<String, Object>> response = controller.healthCheck();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertTrue(body.containsKey("message"));
    }

    @Test
    void testGetConfiguration() {
        ResponseEntity<Map<String, Object>> response = controller.getConfiguration();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.get("count"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mediators = (Map<String, Object>) body.get("mediators");
        assertNotNull(mediators);
        assertEquals(2, mediators.size());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mediator1Config = (Map<String, Object>) mediators.get("mediator-1");
        assertEquals("mediator-1", mediator1Config.get("name"));
        assertEquals("1.0.0", mediator1Config.get("version"));
        assertEquals("First test mediator", mediator1Config.get("description"));
    }

    @Test
    void testGetConfiguration_NoMediators() {
        ReflectionTestUtils.setField(controller, "mediators", null);

        ResponseEntity<Map<String, Object>> response = controller.getConfiguration();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.get("count"));
    }
}
