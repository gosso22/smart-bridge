package com.smartbridge.core.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Service for consuming messages from the queue system.
 * Processes messages from primary queue and handles failures with retry logic.
 */
@Service
public class MessageConsumerService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageConsumerService.class);
    
    private final MessageProducerService messageProducerService;
    private final MessageProcessorRegistry processorRegistry;
    
    public MessageConsumerService(MessageProducerService messageProducerService,
                                  MessageProcessorRegistry processorRegistry) {
        this.messageProducerService = messageProducerService;
        this.processorRegistry = processorRegistry;
    }
    
    /**
     * Listen to primary queue and process messages.
     */
    @RabbitListener(queues = MessageQueueConfig.PRIMARY_QUEUE)
    public void consumeMessage(QueueMessage queueMessage) {
        logger.info("Received message from primary queue: id={}, type={}, retry={}", 
                queueMessage.getId(), queueMessage.getMessageType(), 
                queueMessage.getRetryCount());
        
        try {
            processMessage(queueMessage);
            logger.info("Message processed successfully: id={}", queueMessage.getId());
        } catch (Exception e) {
            handleProcessingFailure(queueMessage, e);
        }
    }
    
    /**
     * Listen to dead letter queue for monitoring and manual intervention.
     */
    @RabbitListener(queues = MessageQueueConfig.DEAD_LETTER_QUEUE)
    public void consumeDeadLetterMessage(QueueMessage queueMessage) {
        logger.error("Message in dead letter queue: id={}, type={}, retries={}, error={}", 
                queueMessage.getId(), queueMessage.getMessageType(), 
                queueMessage.getRetryCount(), queueMessage.getErrorMessage());
        
        // In production, this could trigger alerts, store in database for manual review, etc.
        // For now, just log the failure
    }
    
    private void processMessage(QueueMessage queueMessage) {
        MessageProcessor processor = processorRegistry.getProcessor(queueMessage.getMessageType());
        
        if (processor == null) {
            throw new MessageQueueException(
                    "No processor found for message type: " + queueMessage.getMessageType());
        }
        
        processor.process(queueMessage);
    }
    
    private void handleProcessingFailure(QueueMessage queueMessage, Exception e) {
        logger.error("Failed to process message: id={}, retry={}, error={}", 
                queueMessage.getId(), queueMessage.getRetryCount(), e.getMessage(), e);
        
        queueMessage.setErrorMessage(e.getMessage());
        
        if (queueMessage.canRetry()) {
            logger.info("Sending message to retry queue: id={}, nextRetry={}", 
                    queueMessage.getId(), queueMessage.getRetryCount() + 1);
            messageProducerService.sendToRetryQueue(queueMessage);
        } else {
            logger.warn("Message exceeded max retries, sending to DLQ: id={}", 
                    queueMessage.getId());
            messageProducerService.sendToDeadLetterQueue(queueMessage);
        }
    }
}
