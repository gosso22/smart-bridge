package com.smartbridge.core.queue;

/**
 * Interface for processing different types of queue messages.
 * Implementations should handle specific message types (e.g., FHIR transformation, UCS sync).
 */
public interface MessageProcessor {
    
    /**
     * Process a queue message.
     * 
     * @param message The message to process
     * @throws MessageQueueException if processing fails
     */
    void process(QueueMessage message);
    
    /**
     * Get the message type this processor handles.
     * 
     * @return The message type identifier
     */
    String getMessageType();
}
