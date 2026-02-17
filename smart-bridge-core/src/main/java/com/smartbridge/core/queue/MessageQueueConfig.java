package com.smartbridge.core.queue;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for RabbitMQ message queues.
 * Sets up primary queue, retry queue with exponential backoff, and dead letter queue.
 */
@Configuration
public class MessageQueueConfig {
    
    // Queue names
    public static final String PRIMARY_QUEUE = "smart-bridge.primary";
    public static final String RETRY_QUEUE = "smart-bridge.retry";
    public static final String DEAD_LETTER_QUEUE = "smart-bridge.dlq";
    
    // Exchange names
    public static final String PRIMARY_EXCHANGE = "smart-bridge.exchange";
    public static final String RETRY_EXCHANGE = "smart-bridge.retry.exchange";
    public static final String DLQ_EXCHANGE = "smart-bridge.dlq.exchange";
    
    // Routing keys
    public static final String PRIMARY_ROUTING_KEY = "smart-bridge.primary";
    public static final String RETRY_ROUTING_KEY = "smart-bridge.retry";
    public static final String DLQ_ROUTING_KEY = "smart-bridge.dlq";
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
    
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
    
    // Primary Queue Configuration
    @Bean
    public Queue primaryQueue() {
        return QueueBuilder.durable(PRIMARY_QUEUE)
                .withArgument("x-dead-letter-exchange", RETRY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RETRY_ROUTING_KEY)
                .build();
    }
    
    @Bean
    public DirectExchange primaryExchange() {
        return new DirectExchange(PRIMARY_EXCHANGE);
    }
    
    @Bean
    public Binding primaryBinding(@Qualifier("primaryQueue") Queue primaryQueue, 
                                  @Qualifier("primaryExchange") DirectExchange primaryExchange) {
        return BindingBuilder.bind(primaryQueue)
                .to(primaryExchange)
                .with(PRIMARY_ROUTING_KEY);
    }
    
    // Retry Queue Configuration with exponential backoff
    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", PRIMARY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PRIMARY_ROUTING_KEY)
                .build();
    }
    
    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE);
    }
    
    @Bean
    public Binding retryBinding(@Qualifier("retryQueue") Queue retryQueue, 
                               @Qualifier("retryExchange") DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue)
                .to(retryExchange)
                .with(RETRY_ROUTING_KEY);
    }
    
    // Dead Letter Queue Configuration
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }
    
    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }
    
    @Bean
    public Binding dlqBinding(@Qualifier("deadLetterQueue") Queue deadLetterQueue, 
                             @Qualifier("dlqExchange") DirectExchange dlqExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(dlqExchange)
                .with(DLQ_ROUTING_KEY);
    }
}
