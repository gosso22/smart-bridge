package com.smartbridge.core.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProcessorRegistryTest {
    
    @Mock
    private MessageProcessor processor1;
    
    @Mock
    private MessageProcessor processor2;
    
    @Test
    void testRegistryWithProcessors() {
        when(processor1.getMessageType()).thenReturn("TYPE_1");
        when(processor2.getMessageType()).thenReturn("TYPE_2");
        
        MessageProcessorRegistry registry = new MessageProcessorRegistry(
                Arrays.asList(processor1, processor2));
        
        assertTrue(registry.hasProcessor("TYPE_1"));
        assertTrue(registry.hasProcessor("TYPE_2"));
        assertFalse(registry.hasProcessor("TYPE_3"));
        
        assertEquals(processor1, registry.getProcessor("TYPE_1"));
        assertEquals(processor2, registry.getProcessor("TYPE_2"));
        assertNull(registry.getProcessor("TYPE_3"));
    }
    
    @Test
    void testRegistryWithNoProcessors() {
        MessageProcessorRegistry registry = new MessageProcessorRegistry(Collections.emptyList());
        
        assertFalse(registry.hasProcessor("ANY_TYPE"));
        assertNull(registry.getProcessor("ANY_TYPE"));
    }
    
    @Test
    void testRegisterProcessor() {
        MessageProcessorRegistry registry = new MessageProcessorRegistry(Collections.emptyList());
        
        when(processor1.getMessageType()).thenReturn("NEW_TYPE");
        
        assertFalse(registry.hasProcessor("NEW_TYPE"));
        
        registry.registerProcessor(processor1);
        
        assertTrue(registry.hasProcessor("NEW_TYPE"));
        assertEquals(processor1, registry.getProcessor("NEW_TYPE"));
    }
}
