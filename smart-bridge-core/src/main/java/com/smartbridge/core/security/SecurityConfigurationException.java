package com.smartbridge.core.security;

/**
 * Exception thrown when security configuration fails.
 */
public class SecurityConfigurationException extends RuntimeException {
    
    public SecurityConfigurationException(String message) {
        super(message);
    }
    
    public SecurityConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
