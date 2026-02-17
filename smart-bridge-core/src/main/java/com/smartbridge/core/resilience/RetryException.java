package com.smartbridge.core.resilience;

/**
 * Exception thrown when all retry attempts are exhausted.
 */
public class RetryException extends Exception {

    public RetryException(String message) {
        super(message);
    }

    public RetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
