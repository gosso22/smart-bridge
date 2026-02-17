package com.smartbridge.core.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueMessageTest {
    
    @Test
    void testQueueMessageCreation() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        
        assertNotNull(message.getId());
        assertEquals("test-payload", message.getPayload());
        assertEquals("TEST_TYPE", message.getMessageType());
        assertEquals(0, message.getRetryCount());
        assertEquals(5, message.getMaxRetries());
        assertNotNull(message.getCreatedAt());
        assertNull(message.getLastAttemptAt());
        assertNull(message.getErrorMessage());
    }
    
    @Test
    void testIncrementRetryCount() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        
        assertEquals(0, message.getRetryCount());
        assertNull(message.getLastAttemptAt());
        
        message.incrementRetryCount();
        
        assertEquals(1, message.getRetryCount());
        assertNotNull(message.getLastAttemptAt());
    }
    
    @Test
    void testCanRetry() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        message.setMaxRetries(3);
        
        assertTrue(message.canRetry());
        
        message.setRetryCount(2);
        assertTrue(message.canRetry());
        
        message.setRetryCount(3);
        assertFalse(message.canRetry());
        
        message.setRetryCount(5);
        assertFalse(message.canRetry());
    }
    
    @Test
    void testExponentialBackoff() {
        QueueMessage message = new QueueMessage("test-payload", "TEST_TYPE");
        
        // Test exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s
        assertEquals(1000, message.getRetryDelayMillis());
        
        message.setRetryCount(1);
        assertEquals(2000, message.getRetryDelayMillis());
        
        message.setRetryCount(2);
        assertEquals(4000, message.getRetryDelayMillis());
        
        message.setRetryCount(3);
        assertEquals(8000, message.getRetryDelayMillis());
        
        message.setRetryCount(4);
        assertEquals(16000, message.getRetryDelayMillis());
        
        message.setRetryCount(5);
        assertEquals(32000, message.getRetryDelayMillis());
    }
}
