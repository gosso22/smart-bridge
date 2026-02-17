package com.smartbridge.core.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for producing messages to the queue system.
 * Handles sending messages to primary queue, retry queue, and dead letter queue.
 */
@Service
public class MessageProducerService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageProducerService.class);
    
    private final RabbitTemplate rabbitTemplate;
    
    public MessageProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    /**
     * Send a message to the primary queue for processing.
     */
    public void sendMessage(QueueMessage queueMessage) {
        try {
            logger.info("Sending message to primary queue: id={}, type={}", 
                    queueMessage.getId(), queueMessage.getMessageType());
            
            rabbitTemplate.convertAndSend(
                    MessageQueueConfig.PRIMARY_EXCHANGE,
                    MessageQueueConfig.PRIMARY_ROUTING_KEY,
                    queueMessage
            );
            
            logger.debug("Message sent successfully: id={}", queueMessage.getId());
        } catch (Exception e) {
            logger.error("Failed to send message to primary queue: id={}", 
                    queueMessage.getId(), e);
            throw new MessageQueueException("Failed to send message to queue", e);
        }
    }
    
    /**
     * Send a message to the retry queue with exponential backoff delay.
     */
    public void sendToRetryQueue(QueueMessage queueMessage) {
        if (!queueMessage.canRetry()) {
            logger.warn("Message exceeded max retries, sending to DLQ: id={}, retries={}", 
                    queueMessage.getId(), queueMessage.getRetryCount());
            sendToDeadLetterQueue(queueMessage);
            return;
        }
        
        try {
            queueMessage.incrementRetryCount();
            long delayMillis = queueMessage.getRetryDelayMillis();
            
            logger.info("Sending message to retry queue: id={}, retry={}, delay={}ms", 
                    queueMessage.getId(), queueMessage.getRetryCount(), delayMillis);
            
            Message message = MessageBuilder
                    .withBody(rabbitTemplate.getMessageConverter().toMessage(queueMessage, new MessageProperties()).getBody())
                    .setExpiration(String.valueOf(delayMillis))
                    .build();
            
            rabbitTemplate.send(
                    MessageQueueConfig.RETRY_EXCHANGE,
                    MessageQueueConfig.RETRY_ROUTING_KEY,
                    message
            );
            
            logger.debug("Message sent to retry queue: id={}", queueMessage.getId());
        } catch (Exception e) {
            logger.error("Failed to send message to retry queue: id={}", 
                    queueMessage.getId(), e);
            throw new MessageQueueException("Failed to send message to retry queue", e);
        }
    }
    
    /**
     * Send a message to the dead letter queue for failed messages.
     */
    public void sendToDeadLetterQueue(QueueMessage queueMessage) {
        try {
            logger.warn("Sending message to dead letter queue: id={}, retries={}, error={}", 
                    queueMessage.getId(), queueMessage.getRetryCount(), 
                    queueMessage.getErrorMessage());
            
            rabbitTemplate.convertAndSend(
                    MessageQueueConfig.DLQ_EXCHANGE,
                    MessageQueueConfig.DLQ_ROUTING_KEY,
                    queueMessage
            );
            
            logger.info("Message sent to DLQ: id={}", queueMessage.getId());
        } catch (Exception e) {
            logger.error("Failed to send message to dead letter queue: id={}", 
                    queueMessage.getId(), e);
            throw new MessageQueueException("Failed to send message to DLQ", e);
        }
    }
}
