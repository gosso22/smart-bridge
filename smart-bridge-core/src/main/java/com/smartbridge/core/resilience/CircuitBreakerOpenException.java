package com.smartbridge.core.resilience;

/**
 * Exception thrown when circuit breaker is open and rejects calls.
 */
public class CircuitBreakerOpenException extends Exception {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }

    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
