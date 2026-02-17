package com.smartbridge.core.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageConsumerServiceTest {
    
    @Mock
    private MessageProducerService messageProducerService;
    
    @Mock
    private MessageProcessorRegistry processorRegistry;
    
    @Mock
    private MessageProcessor messageProcessor;
    
    private MessageConsumerService messageConsumerService;
    
    @BeforeEach
    void setUp() {
        messageConsumerService = new MessageConsumerService(
                messageProducerService, processorRegistry);
    }
    
    @Test
    void testConsumeMessageSuccess() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        
        when(processorRegistry.getProcessor("TEST_TYPE")).thenReturn(messageProcessor);
        doNothing().when(messageProcessor).process(message);
        
        messageConsumerService.consumeMessage(message);
        
        verify(processorRegistry).getProcessor("TEST_TYPE");
        verify(messageProcessor).process(message);
        verify(messageProducerService, never()).sendToRetryQueue(any());
        verify(messageProducerService, never()).sendToDeadLetterQueue(any());
    }
    
    @Test
    void testConsumeMessageFailureWithRetry() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setMaxRetries(5);
        
        when(processorRegistry.getProcessor("TEST_TYPE")).thenReturn(messageProcessor);
        doThrow(new RuntimeException("Processing failed")).when(messageProcessor).process(message);
        
        messageConsumerService.consumeMessage(message);
        
        verify(messageProcessor).process(message);
        verify(messageProducerService).sendToRetryQueue(message);
        verify(messageProducerService, never()).sendToDeadLetterQueue(any());
        assertEquals("Processing failed", message.getErrorMessage());
    }
    
    @Test
    void testConsumeMessageFailureExceedsMaxRetries() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setMaxRetries(2);
        message.setRetryCount(2);
        
        when(processorRegistry.getProcessor("TEST_TYPE")).thenReturn(messageProcessor);
        doThrow(new RuntimeException("Processing failed")).when(messageProcessor).process(message);
        
        messageConsumerService.consumeMessage(message);
        
        verify(messageProcessor).process(message);
        verify(messageProducerService).sendToDeadLetterQueue(message);
        verify(messageProducerService, never()).sendToRetryQueue(any());
        assertEquals("Processing failed", message.getErrorMessage());
    }
    
    @Test
    void testConsumeMessageNoProcessor() {
        QueueMessage message = new QueueMessage("test-payload", "UNKNOWN_TYPE");
        
        when(processorRegistry.getProcessor("UNKNOWN_TYPE")).thenReturn(null);
        
        messageConsumerService.consumeMessage(message);
        
        verify(processorRegistry).getProcessor("UNKNOWN_TYPE");
        verify(messageProducerService).sendToRetryQueue(message);
        assertTrue(message.getErrorMessage().contains("No processor found"));
    }
    
    @Test
    void testConsumeDeadLetterMessage() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setRetryCount(5);
        message.setErrorMessage("Max retries exceeded");
        
        // Should just log, no exceptions
        assertDoesNotThrow(() -> messageConsumerService.consumeDeadLetterMessage(message));
    }
}
