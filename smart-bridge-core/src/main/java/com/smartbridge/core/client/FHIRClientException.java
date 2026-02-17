package com.smartbridge.core.client;

/**
 * Exception thrown when FHIR client operations fail.
 */
public class FHIRClientException extends RuntimeException {

    public FHIRClientException(String message) {
        super(message);
    }

    public FHIRClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
