package com.smartbridge.core.monitoring.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for RabbitMQ message queue connectivity.
 * Checks if the message queue is reachable and operational.
 */
@Component
public class MessageQueueHealthIndicator implements HealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(MessageQueueHealthIndicator.class);

    private final RabbitTemplate rabbitTemplate;

    public MessageQueueHealthIndicator(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public Health health() {
        try {
            // Try to get connection factory and check connection
            long startTime = System.currentTimeMillis();
            
            // Attempt to execute a simple operation to verify connectivity
            rabbitTemplate.execute(channel -> {
                // Just checking if we can get a channel
                return channel.isOpen();
            });
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.debug("Message queue health check successful, response time: {}ms", responseTime);
            
            return Health.up()
                    .withDetail("message-queue", "RabbitMQ")
                    .withDetail("response-time-ms", responseTime)
                    .withDetail("status", "connected")
                    .build();
        } catch (Exception e) {
            logger.error("Message queue health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("message-queue", "RabbitMQ")
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "disconnected")
                    .build();
        }
    }
}
