package com.smartbridge.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConcurrentProcessingConfig.
 * Tests thread pool configuration and initialization.
 */
class ConcurrentProcessingConfigTest {

    @Test
    void testTransformationExecutorCreation() {
        ConcurrentProcessingConfig config = new ConcurrentProcessingConfig();
        config.setTransformationCorePoolSize(5);
        config.setTransformationMaxPoolSize(20);
        config.setTransformationQueueCapacity(100);

        Executor executor = config.transformationExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        
        ThreadPoolTaskExecutor threadPoolExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(5, threadPoolExecutor.getCorePoolSize());
        assertEquals(20, threadPoolExecutor.getMaxPoolSize());
    }

    @Test
    void testReverseSyncExecutorCreation() {
        ConcurrentProcessingConfig config = new ConcurrentProcessingConfig();
        config.setReverseSyncCorePoolSize(3);
        config.setReverseSyncMaxPoolSize(10);
        config.setReverseSyncQueueCapacity(50);

        Executor executor = config.reverseSyncExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        
        ThreadPoolTaskExecutor threadPoolExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(3, threadPoolExecutor.getCorePoolSize());
        assertEquals(10, threadPoolExecutor.getMaxPoolSize());
    }

    @Test
    void testDefaultConfiguration() {
        ConcurrentProcessingConfig config = new ConcurrentProcessingConfig();

        assertEquals(5, config.getTransformationCorePoolSize());
        assertEquals(20, config.getTransformationMaxPoolSize());
        assertEquals(100, config.getTransformationQueueCapacity());
        assertEquals(60, config.getTransformationKeepAliveSeconds());
        assertEquals("transformation-", config.getTransformationThreadNamePrefix());

        assertEquals(3, config.getReverseSyncCorePoolSize());
        assertEquals(10, config.getReverseSyncMaxPoolSize());
        assertEquals(50, config.getReverseSyncQueueCapacity());
        assertEquals(60, config.getReverseSyncKeepAliveSeconds());
        assertEquals("reverse-sync-", config.getReverseSyncThreadNamePrefix());
    }

    @Test
    void testConfigurationSetters() {
        ConcurrentProcessingConfig config = new ConcurrentProcessingConfig();

        config.setTransformationCorePoolSize(10);
        config.setTransformationMaxPoolSize(30);
        config.setTransformationQueueCapacity(200);
        config.setTransformationKeepAliveSeconds(120);
        config.setTransformationThreadNamePrefix("custom-transform-");

        assertEquals(10, config.getTransformationCorePoolSize());
        assertEquals(30, config.getTransformationMaxPoolSize());
        assertEquals(200, config.getTransformationQueueCapacity());
        assertEquals(120, config.getTransformationKeepAliveSeconds());
        assertEquals("custom-transform-", config.getTransformationThreadNamePrefix());
    }
}
