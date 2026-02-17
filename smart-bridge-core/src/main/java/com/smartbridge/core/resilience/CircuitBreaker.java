package com.smartbridge.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit breaker pattern implementation for external service calls.
 * Monitors failure rates and opens circuit after consecutive failures.
 * Implements half-open state for recovery testing.
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String name;
    private final int failureThreshold;
    private final Duration cooldownPeriod;
    private final int successThreshold;

    private final AtomicReference<State> state;
    private final AtomicInteger consecutiveFailures;
    private final AtomicInteger consecutiveSuccesses;
    private final AtomicReference<Instant> lastFailureTime;

    public enum State {
        CLOSED,      // Normal operation
        OPEN,        // Circuit is open, rejecting calls
        HALF_OPEN    // Testing if service recovered
    }

    /**
     * Create a circuit breaker with default settings.
     * Default: 5 failures, 30 second cooldown, 3 successes to close
     */
    public CircuitBreaker(String name) {
        this(name, 5, Duration.ofSeconds(30), 3);
    }

    /**
     * Create a circuit breaker with custom settings.
     *
     * @param name Service name for logging
     * @param failureThreshold Number of consecutive failures to open circuit
     * @param cooldownPeriod Time to wait before attempting half-open
     * @param successThreshold Number of successes in half-open to close circuit
     */
    public CircuitBreaker(String name, int failureThreshold, Duration cooldownPeriod, int successThreshold) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldownPeriod = cooldownPeriod;
        this.successThreshold = successThreshold;

        this.state = new AtomicReference<>(State.CLOSED);
        this.consecutiveFailures = new AtomicInteger(0);
        this.consecutiveSuccesses = new AtomicInteger(0);
        this.lastFailureTime = new AtomicReference<>();

        logger.info("Circuit breaker created for {}: failureThreshold={}, cooldownPeriod={}s, successThreshold={}",
            name, failureThreshold, cooldownPeriod.getSeconds(), successThreshold);
    }

    /**
     * Execute a call through the circuit breaker.
     *
     * @param supplier The operation to execute
     * @return Result of the operation
     * @throws CircuitBreakerOpenException if circuit is open
     * @throws Exception if the operation fails
     */
    public <T> T execute(Supplier<T> supplier) throws Exception {
        State currentState = getState();

        if (currentState == State.OPEN) {
            logger.warn("Circuit breaker {} is OPEN, rejecting call", name);
            throw new CircuitBreakerOpenException("Circuit breaker is open for: " + name);
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    /**
     * Get current state, transitioning to HALF_OPEN if cooldown expired.
     */
    public State getState() {
        State currentState = state.get();

        if (currentState == State.OPEN) {
            Instant lastFailure = lastFailureTime.get();
            if (lastFailure != null && Duration.between(lastFailure, Instant.now()).compareTo(cooldownPeriod) >= 0) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    logger.info("Circuit breaker {} transitioning to HALF_OPEN after cooldown", name);
                    consecutiveSuccesses.set(0);
                    return State.HALF_OPEN;
                }
            }
        }

        return state.get();
    }

    /**
     * Record a successful operation.
     */
    private void onSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int successes = consecutiveSuccesses.incrementAndGet();
            logger.debug("Circuit breaker {} success in HALF_OPEN: {}/{}", name, successes, successThreshold);

            if (successes >= successThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    logger.info("Circuit breaker {} closed after {} successful operations", name, successes);
                    consecutiveFailures.set(0);
                    consecutiveSuccesses.set(0);
                }
            }
        } else if (currentState == State.CLOSED) {
            consecutiveFailures.set(0);
        }
    }

    /**
     * Record a failed operation.
     */
    private void onFailure() {
        State currentState = state.get();
        lastFailureTime.set(Instant.now());

        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                logger.warn("Circuit breaker {} reopened after failure in HALF_OPEN state", name);
                consecutiveSuccesses.set(0);
            }
        } else if (currentState == State.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();
            logger.debug("Circuit breaker {} failure count: {}/{}", name, failures, failureThreshold);

            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    logger.error("Circuit breaker {} opened after {} consecutive failures", name, failures);
                }
            }
        }
    }

    /**
     * Manually reset the circuit breaker to closed state.
     */
    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        lastFailureTime.set(null);
        logger.info("Circuit breaker {} manually reset to CLOSED", name);
    }

    /**
     * Get circuit breaker metrics.
     */
    public CircuitBreakerMetrics getMetrics() {
        return new CircuitBreakerMetrics(
            name,
            state.get(),
            consecutiveFailures.get(),
            consecutiveSuccesses.get(),
            lastFailureTime.get()
        );
    }

    public String getName() {
        return name;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public Duration getCooldownPeriod() {
        return cooldownPeriod;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    /**
     * Circuit breaker metrics for monitoring.
     */
    public static class CircuitBreakerMetrics {
        private final String name;
        private final State state;
        private final int consecutiveFailures;
        private final int consecutiveSuccesses;
        private final Instant lastFailureTime;

        public CircuitBreakerMetrics(String name, State state, int consecutiveFailures,
                                    int consecutiveSuccesses, Instant lastFailureTime) {
            this.name = name;
            this.state = state;
            this.consecutiveFailures = consecutiveFailures;
            this.consecutiveSuccesses = consecutiveSuccesses;
            this.lastFailureTime = lastFailureTime;
        }

        public String getName() {
            return name;
        }

        public State getState() {
            return state;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public int getConsecutiveSuccesses() {
            return consecutiveSuccesses;
        }

        public Instant getLastFailureTime() {
            return lastFailureTime;
        }
    }
}
