package com.smartbridge.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for concurrent processing capabilities in Smart Bridge.
 * Provides thread pool configuration for transformation services with proper
 * resource management and synchronization.
 * 
 * Requirements: 7.2 - Concurrent processing capability
 */
@Configuration
@ConfigurationProperties(prefix = "smartbridge.concurrent")
public class ConcurrentProcessingConfig {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentProcessingConfig.class);

    // Default configuration values
    private int transformationCorePoolSize = 5;
    private int transformationMaxPoolSize = 20;
    private int transformationQueueCapacity = 100;
    private int transformationKeepAliveSeconds = 60;
    private String transformationThreadNamePrefix = "transformation-";

    private int reverseSyncCorePoolSize = 3;
    private int reverseSyncMaxPoolSize = 10;
    private int reverseSyncQueueCapacity = 50;
    private int reverseSyncKeepAliveSeconds = 60;
    private String reverseSyncThreadNamePrefix = "reverse-sync-";

    /**
     * Thread pool executor for transformation operations.
     * Handles concurrent UCS to FHIR and FHIR to UCS transformations.
     */
    @Bean(name = "transformationExecutor")
    public Executor transformationExecutor() {
        logger.info("Initializing transformation thread pool: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
            transformationCorePoolSize, transformationMaxPoolSize, transformationQueueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(transformationCorePoolSize);
        executor.setMaxPoolSize(transformationMaxPoolSize);
        executor.setQueueCapacity(transformationQueueCapacity);
        executor.setKeepAliveSeconds(transformationKeepAliveSeconds);
        executor.setThreadNamePrefix(transformationThreadNamePrefix);
        executor.setRejectedExecutionHandler(new TransformationRejectedExecutionHandler());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        logger.info("Transformation thread pool initialized successfully");
        return executor;
    }

    /**
     * Thread pool executor for reverse sync operations.
     * Handles concurrent FHIR to UCS synchronization flows.
     */
    @Bean(name = "reverseSyncExecutor")
    public Executor reverseSyncExecutor() {
        logger.info("Initializing reverse sync thread pool: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
            reverseSyncCorePoolSize, reverseSyncMaxPoolSize, reverseSyncQueueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(reverseSyncCorePoolSize);
        executor.setMaxPoolSize(reverseSyncMaxPoolSize);
        executor.setQueueCapacity(reverseSyncQueueCapacity);
        executor.setKeepAliveSeconds(reverseSyncKeepAliveSeconds);
        executor.setThreadNamePrefix(reverseSyncThreadNamePrefix);
        executor.setRejectedExecutionHandler(new ReverseSyncRejectedExecutionHandler());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        logger.info("Reverse sync thread pool initialized successfully");
        return executor;
    }

    /**
     * Custom rejected execution handler for transformation tasks.
     * Logs rejection and applies CallerRuns policy as fallback.
     */
    private static class TransformationRejectedExecutionHandler implements RejectedExecutionHandler {
        private static final Logger logger = LoggerFactory.getLogger(TransformationRejectedExecutionHandler.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            logger.warn("Transformation task rejected - thread pool at capacity. " +
                "Active threads: {}, Queue size: {}, Pool size: {}",
                executor.getActiveCount(), executor.getQueue().size(), executor.getPoolSize());

            // Use CallerRuns policy - execute in caller's thread
            if (!executor.isShutdown()) {
                logger.info("Executing rejected transformation task in caller's thread");
                r.run();
            }
        }
    }

    /**
     * Custom rejected execution handler for reverse sync tasks.
     * Logs rejection and applies CallerRuns policy as fallback.
     */
    private static class ReverseSyncRejectedExecutionHandler implements RejectedExecutionHandler {
        private static final Logger logger = LoggerFactory.getLogger(ReverseSyncRejectedExecutionHandler.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            logger.warn("Reverse sync task rejected - thread pool at capacity. " +
                "Active threads: {}, Queue size: {}, Pool size: {}",
                executor.getActiveCount(), executor.getQueue().size(), executor.getPoolSize());

            // Use CallerRuns policy - execute in caller's thread
            if (!executor.isShutdown()) {
                logger.info("Executing rejected reverse sync task in caller's thread");
                r.run();
            }
        }
    }

    // Getters and setters for configuration properties
    public int getTransformationCorePoolSize() {
        return transformationCorePoolSize;
    }

    public void setTransformationCorePoolSize(int transformationCorePoolSize) {
        this.transformationCorePoolSize = transformationCorePoolSize;
    }

    public int getTransformationMaxPoolSize() {
        return transformationMaxPoolSize;
    }

    public void setTransformationMaxPoolSize(int transformationMaxPoolSize) {
        this.transformationMaxPoolSize = transformationMaxPoolSize;
    }

    public int getTransformationQueueCapacity() {
        return transformationQueueCapacity;
    }

    public void setTransformationQueueCapacity(int transformationQueueCapacity) {
        this.transformationQueueCapacity = transformationQueueCapacity;
    }

    public int getTransformationKeepAliveSeconds() {
        return transformationKeepAliveSeconds;
    }

    public void setTransformationKeepAliveSeconds(int transformationKeepAliveSeconds) {
        this.transformationKeepAliveSeconds = transformationKeepAliveSeconds;
    }

    public String getTransformationThreadNamePrefix() {
        return transformationThreadNamePrefix;
    }

    public void setTransformationThreadNamePrefix(String transformationThreadNamePrefix) {
        this.transformationThreadNamePrefix = transformationThreadNamePrefix;
    }

    public int getReverseSyncCorePoolSize() {
        return reverseSyncCorePoolSize;
    }

    public void setReverseSyncCorePoolSize(int reverseSyncCorePoolSize) {
        this.reverseSyncCorePoolSize = reverseSyncCorePoolSize;
    }

    public int getReverseSyncMaxPoolSize() {
        return reverseSyncMaxPoolSize;
    }

    public void setReverseSyncMaxPoolSize(int reverseSyncMaxPoolSize) {
        this.reverseSyncMaxPoolSize = reverseSyncMaxPoolSize;
    }

    public int getReverseSyncQueueCapacity() {
        return reverseSyncQueueCapacity;
    }

    public void setReverseSyncQueueCapacity(int reverseSyncQueueCapacity) {
        this.reverseSyncQueueCapacity = reverseSyncQueueCapacity;
    }

    public int getReverseSyncKeepAliveSeconds() {
        return reverseSyncKeepAliveSeconds;
    }

    public void setReverseSyncKeepAliveSeconds(int reverseSyncKeepAliveSeconds) {
        this.reverseSyncKeepAliveSeconds = reverseSyncKeepAliveSeconds;
    }

    public String getReverseSyncThreadNamePrefix() {
        return reverseSyncThreadNamePrefix;
    }

    public void setReverseSyncThreadNamePrefix(String reverseSyncThreadNamePrefix) {
        this.reverseSyncThreadNamePrefix = reverseSyncThreadNamePrefix;
    }
}
