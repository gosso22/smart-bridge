package com.smartbridge.core.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for message processors.
 * Manages registration and lookup of processors by message type.
 */
@Component
public class MessageProcessorRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageProcessorRegistry.class);
    
    private final Map<String, MessageProcessor> processors = new ConcurrentHashMap<>();
    
    public MessageProcessorRegistry(List<MessageProcessor> processorList) {
        for (MessageProcessor processor : processorList) {
            registerProcessor(processor);
        }
    }
    
    /**
     * Register a message processor.
     */
    public void registerProcessor(MessageProcessor processor) {
        String messageType = processor.getMessageType();
        processors.put(messageType, processor);
        logger.info("Registered message processor: type={}, class={}", 
                messageType, processor.getClass().getSimpleName());
    }
    
    /**
     * Get a processor for a specific message type.
     */
    public MessageProcessor getProcessor(String messageType) {
        return processors.get(messageType);
    }
    
    /**
     * Check if a processor is registered for a message type.
     */
    public boolean hasProcessor(String messageType) {
        return processors.containsKey(messageType);
    }
}
