package com.smartbridge.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker("TestService", 3, Duration.ofSeconds(1), 2);
    }

    @Test
    void testInitialStateClosed() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testSuccessfulExecution() throws Exception {
        String result = circuitBreaker.execute(() -> "success");
        assertEquals("success", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testCircuitOpensAfterFailures() {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void testCircuitBreakerRejectsCallsWhenOpen() {
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Verify circuit is open and rejects calls
        assertThrows(CircuitBreakerOpenException.class, () -> {
            circuitBreaker.execute(() -> "should not execute");
        });
    }

    @Test
    void testCircuitTransitionsToHalfOpen() throws InterruptedException {
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Wait for cooldown
        Thread.sleep(1100);
        
        // Check state transitions to HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void testCircuitClosesAfterSuccessesInHalfOpen() throws Exception {
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Wait for cooldown
        Thread.sleep(1100);
        
        // Execute successful operations
        circuitBreaker.execute(() -> "success1");
        circuitBreaker.execute(() -> "success2");
        
        // Circuit should be closed
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testCircuitReopensOnFailureInHalfOpen() throws InterruptedException {
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Wait for cooldown
        Thread.sleep(1100);
        
        // Fail in half-open state
        try {
            circuitBreaker.execute(() -> {
                throw new RuntimeException("Test failure");
            });
        } catch (Exception e) {
            // Expected
        }
        
        // Circuit should be open again
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void testManualReset() {
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Reset manually
        circuitBreaker.reset();
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testMetrics() {
        CircuitBreaker.CircuitBreakerMetrics metrics = circuitBreaker.getMetrics();
        
        assertNotNull(metrics);
        assertEquals("TestService", metrics.getName());
        assertEquals(CircuitBreaker.State.CLOSED, metrics.getState());
        assertEquals(0, metrics.getConsecutiveFailures());
    }
}
