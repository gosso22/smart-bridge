package com.smartbridge.mediators.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenHIMClientImplTest {

    private OpenHIMClientImpl openHIMClient;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        openHIMClient = new OpenHIMClientImpl();
        restTemplate = mock(RestTemplate.class);
        
        ReflectionTestUtils.setField(openHIMClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(openHIMClient, "openHIMCoreUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(openHIMClient, "openHIMUsername", "test@openhim.org");
        ReflectionTestUtils.setField(openHIMClient, "openHIMPassword", "password");
        ReflectionTestUtils.setField(openHIMClient, "openHIMEnabled", true);
    }

    @Test
    void testRegisterMediator_Success() {
        MediatorRegistration registration = new MediatorRegistration(
            "test-mediator",
            "1.0.0",
            "Test mediator",
            new HashMap<>(),
            new HashMap<>()
        );

        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.CREATED);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        boolean result = openHIMClient.registerMediator(registration);

        assertTrue(result);
        verify(restTemplate).exchange(
            contains("/mediators"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testRegisterMediator_Disabled() {
        ReflectionTestUtils.setField(openHIMClient, "openHIMEnabled", false);

        MediatorRegistration registration = new MediatorRegistration(
            "test-mediator",
            "1.0.0",
            "Test mediator",
            new HashMap<>(),
            new HashMap<>()
        );

        boolean result = openHIMClient.registerMediator(registration);

        assertFalse(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void testRegisterMediator_Failure() {
        MediatorRegistration registration = new MediatorRegistration(
            "test-mediator",
            "1.0.0",
            "Test mediator",
            new HashMap<>(),
            new HashMap<>()
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Connection failed"));

        boolean result = openHIMClient.registerMediator(registration);

        assertFalse(result);
    }

    @Test
    void testSendHeartbeat_Success() {
        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        boolean result = openHIMClient.sendHeartbeat("test-mediator");

        assertTrue(result);
        verify(restTemplate).exchange(
            contains("/heartbeat"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testSendHeartbeat_Disabled() {
        ReflectionTestUtils.setField(openHIMClient, "openHIMEnabled", false);

        boolean result = openHIMClient.sendHeartbeat("test-mediator");

        assertFalse(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void testReportTransaction_Success() {
        TransactionReport transaction = TransactionReport.builder()
            .transactionId("tx-123")
            .mediatorName("test-mediator")
            .status("SUCCESS")
            .httpStatusCode(200)
            .startTime(Instant.now())
            .endTime(Instant.now())
            .build();

        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.CREATED);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        boolean result = openHIMClient.reportTransaction(transaction);

        assertTrue(result);
        verify(restTemplate).exchange(
            contains("/transactions"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testIsOpenHIMReachable_Success() {
        ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        boolean result = openHIMClient.isOpenHIMReachable();

        assertTrue(result);
        verify(restTemplate).exchange(
            contains("/heartbeat"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testIsOpenHIMReachable_Failure() {
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Connection failed"));

        boolean result = openHIMClient.isOpenHIMReachable();

        assertFalse(result);
    }

    @Test
    void testIsOpenHIMReachable_Disabled() {
        ReflectionTestUtils.setField(openHIMClient, "openHIMEnabled", false);

        boolean result = openHIMClient.isOpenHIMReachable();

        assertFalse(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }
}
