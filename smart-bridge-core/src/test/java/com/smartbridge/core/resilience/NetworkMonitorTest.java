package com.smartbridge.core.resilience;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMonitorTest {

    private NetworkMonitor networkMonitor;

    @BeforeEach
    void setUp() {
        networkMonitor = new NetworkMonitor(Duration.ofSeconds(1), 1000);
    }

    @AfterEach
    void tearDown() {
        if (networkMonitor.isRunning()) {
            networkMonitor.stop();
        }
    }

    @Test
    void testRegisterService() {
        networkMonitor.registerService("TestService", "http://localhost:8080/health");
        
        Map<String, Boolean> statuses = networkMonitor.getAllServiceStatuses();
        assertTrue(statuses.containsKey("TestService"));
    }

    @Test
    void testStartAndStop() {
        assertFalse(networkMonitor.isRunning());
        
        networkMonitor.start();
        assertTrue(networkMonitor.isRunning());
        
        networkMonitor.stop();
        assertFalse(networkMonitor.isRunning());
    }

    @Test
    void testServiceAvailability() {
        networkMonitor.registerService("TestService", "http://localhost:8080/health");
        
        // Before starting, service should be unavailable (no check performed yet)
        assertFalse(networkMonitor.isServiceAvailable("TestService"));
    }

    @Test
    void testCheckServiceNow() {
        networkMonitor.registerService("TestService", "http://192.0.2.1:1/health");
        
        boolean available = networkMonitor.checkServiceNow("TestService");
        assertFalse(available); // Unreachable address
        
        Instant lastCheck = networkMonitor.getLastCheckTime("TestService");
        assertNotNull(lastCheck);
    }

    @Test
    void testGetAllServiceStatuses() {
        networkMonitor.registerService("Service1", "http://localhost:8080/health");
        networkMonitor.registerService("Service2", "http://localhost:8081/health");
        
        Map<String, Boolean> statuses = networkMonitor.getAllServiceStatuses();
        
        assertEquals(2, statuses.size());
        assertTrue(statuses.containsKey("Service1"));
        assertTrue(statuses.containsKey("Service2"));
    }

    @Test
    void testUnregisteredService() {
        assertFalse(networkMonitor.isServiceAvailable("NonExistent"));
        assertNull(networkMonitor.getLastCheckTime("NonExistent"));
        assertNull(networkMonitor.getLastAvailableTime("NonExistent"));
    }

    @Test
    void testPeriodicChecks() throws InterruptedException {
        networkMonitor.registerService("TestService", "http://localhost:8080/health");
        networkMonitor.start();
        
        Instant firstCheck = networkMonitor.getLastCheckTime("TestService");
        
        // Wait for next check cycle
        Thread.sleep(1500);
        
        Instant secondCheck = networkMonitor.getLastCheckTime("TestService");
        
        assertNotNull(firstCheck);
        assertNotNull(secondCheck);
        assertTrue(secondCheck.isAfter(firstCheck));
    }

    @Test
    void testMultipleStartCallsIdempotent() {
        networkMonitor.start();
        assertTrue(networkMonitor.isRunning());
        
        networkMonitor.start();
        assertTrue(networkMonitor.isRunning());
    }

    @Test
    void testMultipleStopCallsIdempotent() {
        networkMonitor.start();
        networkMonitor.stop();
        assertFalse(networkMonitor.isRunning());
        
        networkMonitor.stop();
        assertFalse(networkMonitor.isRunning());
    }
}
