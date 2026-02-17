package com.smartbridge.core.queue;

/**
 * Exception thrown when message queue operations fail.
 */
public class MessageQueueException extends RuntimeException {
    
    public MessageQueueException(String message) {
        super(message);
    }
    
    public MessageQueueException(String message, Throwable cause) {
        super(message, cause);
    }
}
