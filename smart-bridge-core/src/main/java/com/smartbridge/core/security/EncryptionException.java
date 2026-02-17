package com.smartbridge.core.security;

/**
 * Exception thrown when encryption or decryption operations fail.
 */
public class EncryptionException extends RuntimeException {
    
    public EncryptionException(String message) {
        super(message);
    }
    
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
