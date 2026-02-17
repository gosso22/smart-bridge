package com.smartbridge.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy.Builder("TestOperation")
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .maxDelay(Duration.ofMillis(100))
            .backoffMultiplier(2.0)
            .build();
    }

    @Test
    void testSuccessfulExecutionOnFirstAttempt() throws Exception {
        String result = retryPolicy.execute(() -> "success");
        assertEquals("success", result);
    }

    @Test
    void testSuccessfulExecutionAfterRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = retryPolicy.execute(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Temporary failure");
            }
            return "success";
        });
        
        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testAllRetriesExhausted() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        assertThrows(RetryException.class, () -> {
            retryPolicy.execute(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Persistent failure");
            });
        });
        
        assertEquals(3, attempts.get());
    }

    @Test
    void testRetryCondition() {
        RetryPolicy customPolicy = new RetryPolicy.Builder("CustomOperation")
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .retryCondition(e -> e instanceof IllegalStateException)
            .build();
        
        // Should not retry for RuntimeException
        assertThrows(RuntimeException.class, () -> {
            customPolicy.execute(() -> {
                throw new RuntimeException("Not retryable");
            });
        });
    }

    @Test
    void testExponentialBackoff() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        try {
            retryPolicy.execute(() -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("Temporary failure");
                }
                return "success";
            });
        } catch (Exception e) {
            // Expected
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Should have delays: 10ms + 20ms = 30ms minimum
        assertTrue(duration >= 30, "Expected at least 30ms delay, got " + duration + "ms");
    }

    @Test
    void testBuilderDefaults() {
        RetryPolicy defaultPolicy = new RetryPolicy("DefaultOperation");
        
        assertEquals(3, defaultPolicy.getMaxAttempts());
        assertEquals(Duration.ofSeconds(1), defaultPolicy.getInitialDelay());
        assertEquals(Duration.ofSeconds(32), defaultPolicy.getMaxDelay());
        assertEquals(2.0, defaultPolicy.getBackoffMultiplier());
    }

    @Test
    void testMaxDelayLimit() throws Exception {
        RetryPolicy limitedPolicy = new RetryPolicy.Builder("LimitedOperation")
            .maxAttempts(5)
            .initialDelay(Duration.ofMillis(10))
            .maxDelay(Duration.ofMillis(50))
            .backoffMultiplier(2.0)
            .build();
        
        AtomicInteger attempts = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        try {
            limitedPolicy.execute(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Persistent failure");
            });
        } catch (RetryException e) {
            // Expected
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Delays should be: 10, 20, 40, 50 (capped) = 120ms minimum
        // Allow some tolerance for timing variations
        assertTrue(duration >= 100, "Expected at least 100ms delay, got " + duration + "ms");
        assertEquals(5, attempts.get());
    }
}
