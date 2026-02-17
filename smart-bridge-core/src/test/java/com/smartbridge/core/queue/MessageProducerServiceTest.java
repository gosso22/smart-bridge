package com.smartbridge.core.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageProducerServiceTest {
    
    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @Mock
    private MessageConverter messageConverter;
    
    private MessageProducerService messageProducerService;
    
    @BeforeEach
    void setUp() {
        messageProducerService = new MessageProducerService(rabbitTemplate);
    }
    
    @Test
    void testSendMessage() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        
        messageProducerService.sendMessage(message);
        
        verify(rabbitTemplate).convertAndSend(
                eq(MessageQueueConfig.PRIMARY_EXCHANGE),
                eq(MessageQueueConfig.PRIMARY_ROUTING_KEY),
                eq(message)
        );
    }
    
    @Test
    void testSendToRetryQueue() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setMaxRetries(5);
        
        Message mockMessage = mock(Message.class);
        when(mockMessage.getBody()).thenReturn("test-body".getBytes());
        when(rabbitTemplate.getMessageConverter()).thenReturn(messageConverter);
        when(messageConverter.toMessage(any(), any())).thenReturn(mockMessage);
        
        messageProducerService.sendToRetryQueue(message);
        
        assertEquals(1, message.getRetryCount());
        assertNotNull(message.getLastAttemptAt());
        
        verify(rabbitTemplate).send(
                eq(MessageQueueConfig.RETRY_EXCHANGE),
                eq(MessageQueueConfig.RETRY_ROUTING_KEY),
                any(Message.class)
        );
    }
    
    @Test
    void testSendToRetryQueueExceedsMaxRetries() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setMaxRetries(2);
        message.setRetryCount(2);
        
        messageProducerService.sendToRetryQueue(message);
        
        verify(rabbitTemplate).convertAndSend(
                eq(MessageQueueConfig.DLQ_EXCHANGE),
                eq(MessageQueueConfig.DLQ_ROUTING_KEY),
                eq(message)
        );
        verify(rabbitTemplate, never()).send(
                eq(MessageQueueConfig.RETRY_EXCHANGE),
                anyString(),
                any(Message.class)
        );
    }
    
    @Test
    void testSendToDeadLetterQueue() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setErrorMessage("Processing failed");
        
        messageProducerService.sendToDeadLetterQueue(message);
        
        verify(rabbitTemplate).convertAndSend(
                eq(MessageQueueConfig.DLQ_EXCHANGE),
                eq(MessageQueueConfig.DLQ_ROUTING_KEY),
                eq(message)
        );
    }
    
    @Test
    void testExponentialBackoffDelay() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        
        assertEquals(1000, message.getRetryDelayMillis()); // 2^0 * 1000 = 1s
        
        message.incrementRetryCount();
        assertEquals(2000, message.getRetryDelayMillis()); // 2^1 * 1000 = 2s
        
        message.incrementRetryCount();
        assertEquals(4000, message.getRetryDelayMillis()); // 2^2 * 1000 = 4s
        
        message.incrementRetryCount();
        assertEquals(8000, message.getRetryDelayMillis()); // 2^3 * 1000 = 8s
        
        message.incrementRetryCount();
        assertEquals(16000, message.getRetryDelayMillis()); // 2^4 * 1000 = 16s
        
        message.incrementRetryCount();
        assertEquals(32000, message.getRetryDelayMillis()); // 2^5 * 1000 = 32s
    }
}
