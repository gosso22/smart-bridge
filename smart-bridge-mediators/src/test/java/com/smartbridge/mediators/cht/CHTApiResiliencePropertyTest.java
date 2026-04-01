package com.smartbridge.mediators.cht;

import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.CircuitBreakerOpenException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CHT API resilience via circuit breaker.
 * Feature: cht-integration, Property 9: CHT API Resilience
 *
 * After N consecutive failures, circuit opens; after cooldown, half-open allows test request.
 * Validates: Requirements 8.2-8.5
 */
class CHTApiResiliencePropertyTest {

    // ==================== Circuit Opens After N Failures ====================

    @Property(tries = 50)
    void circuitOpensAfterExactlyNConsecutiveFailures(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold) {

        CircuitBreaker cb = new CircuitBreaker("test-cht",
            failureThreshold, Duration.ofSeconds(30), 3);

        // Record failures up to threshold - 1: circuit stays closed
        for (int i = 0; i < failureThreshold - 1; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("failure"); });
            } catch (Exception ignored) {}

            assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
                "Circuit should stay CLOSED after " + (i + 1) + " failures (threshold=" + failureThreshold + ")");
        }

        // The Nth failure should open the circuit
        try {
            cb.execute(() -> { throw new RuntimeException("threshold failure"); });
        } catch (Exception ignored) {}

        assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
            "Circuit should be OPEN after " + failureThreshold + " consecutive failures");
    }

    // ==================== Open Circuit Rejects Calls ====================

    @Property(tries = 50)
    void openCircuitAlwaysRejectsCalls(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold) {

        CircuitBreaker cb = new CircuitBreaker("test-cht",
            failureThreshold, Duration.ofHours(1), 3);

        // Open the circuit
        for (int i = 0; i < failureThreshold; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fail"); });
            } catch (Exception ignored) {}
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        // All subsequent calls should be rejected
        assertThrows(CircuitBreakerOpenException.class,
            () -> cb.execute(() -> "should not execute"));
    }

    // ==================== Success Resets Failure Count ====================

    @Property(tries = 50)
    void successResetsConsecutiveFailureCount(
            @ForAll @IntRange(min = 2, max = 10) int failureThreshold,
            @ForAll @IntRange(min = 1, max = 5) int failuresBeforeSuccess) {

        Assume.that(failuresBeforeSuccess < failureThreshold);

        CircuitBreaker cb = new CircuitBreaker("test-cht",
            failureThreshold, Duration.ofSeconds(30), 3);

        // Accumulate some failures (but fewer than threshold)
        for (int i = 0; i < failuresBeforeSuccess; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fail"); });
            } catch (Exception ignored) {}
        }

        // A success resets the counter
        try {
            cb.execute(() -> "success");
        } catch (Exception ignored) {}

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
            "Success should reset failure count, keeping circuit CLOSED");

        // Now we need failureThreshold more failures to open (not failureThreshold - failuresBeforeSuccess)
        for (int i = 0; i < failureThreshold - 1; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fail after reset"); });
            } catch (Exception ignored) {}
        }

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
            "After reset, circuit should still be CLOSED with " + (failureThreshold - 1) + " failures");
    }

    // ==================== Half-Open Transitions ====================

    @Property(tries = 20)
    void halfOpenClosesAfterEnoughSuccesses(
            @ForAll @IntRange(min = 1, max = 5) int successThreshold) {

        CircuitBreaker cb = new CircuitBreaker("test-cht",
            1, Duration.ofMillis(1), successThreshold);

        // Open the circuit
        try {
            cb.execute(() -> { throw new RuntimeException("fail"); });
        } catch (Exception ignored) {}
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        // Wait for cooldown to expire (1ms)
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        // After cooldown, should transition to HALF_OPEN
        CircuitBreaker.State stateAfterCooldown = cb.getState();
        assertEquals(CircuitBreaker.State.HALF_OPEN, stateAfterCooldown,
            "Circuit should be HALF_OPEN after cooldown");

        // Record successes in half-open
        for (int i = 0; i < successThreshold; i++) {
            try {
                cb.execute(() -> "success");
            } catch (Exception ignored) {}
        }

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
            "Circuit should close after " + successThreshold + " successes in HALF_OPEN");
    }

    @Property(tries = 20)
    void halfOpenReopensOnFailure() {
        CircuitBreaker cb = new CircuitBreaker("test-cht",
            1, Duration.ofMillis(1), 3);

        // Open the circuit
        try {
            cb.execute(() -> { throw new RuntimeException("fail"); });
        } catch (Exception ignored) {}

        // Wait for cooldown
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

        // Failure in half-open should reopen
        try {
            cb.execute(() -> { throw new RuntimeException("fail in half-open"); });
        } catch (Exception ignored) {}

        assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
            "Failure in HALF_OPEN should reopen the circuit");
    }

    // ==================== Reset Always Returns to Closed ====================

    @Property(tries = 50)
    void resetAlwaysReturnsToClosed(
            @ForAll @IntRange(min = 1, max = 10) int failureThreshold) {

        CircuitBreaker cb = new CircuitBreaker("test-cht",
            failureThreshold, Duration.ofHours(1), 3);

        // Open the circuit
        for (int i = 0; i < failureThreshold; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fail"); });
            } catch (Exception ignored) {}
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        cb.reset();

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
            "Reset should always return circuit to CLOSED state");

        // Should accept calls again
        assertDoesNotThrow(() -> cb.execute(() -> "works again"));
    }
}
