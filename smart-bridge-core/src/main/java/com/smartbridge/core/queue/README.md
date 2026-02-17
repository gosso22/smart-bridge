# Message Queue Infrastructure

## Overview

This package provides a robust message queue infrastructure using RabbitMQ with Spring AMQP. It implements reliable message processing with retry logic, exponential backoff, and dead letter queue handling.

## Components

### QueueMessage
Represents a message in the queue system with:
- Unique message ID
- Payload (JSON string)
- Message type for routing to appropriate processors
- Retry count and max retries configuration
- Timestamps for creation and last attempt
- Error message tracking

### MessageQueueConfig
Spring configuration that sets up:
- **Primary Queue**: Main processing queue with dead letter routing to retry queue
- **Retry Queue**: Handles failed messages with exponential backoff (1s, 2s, 4s, 8s, 16s, 32s)
- **Dead Letter Queue (DLQ)**: Stores messages that exceeded max retry attempts

### MessageProducerService
Service for sending messages to queues:
- `sendMessage()`: Send to primary queue for processing
- `sendToRetryQueue()`: Send to retry queue with exponential backoff delay
- `sendToDeadLetterQueue()`: Send permanently failed messages to DLQ

### MessageConsumerService
Service for consuming and processing messages:
- Listens to primary queue and processes messages
- Handles failures with automatic retry logic
- Monitors dead letter queue for alerting

### MessageProcessor Interface
Interface for implementing message type-specific processors:
```java
public interface MessageProcessor {
    void process(QueueMessage message);
    String getMessageType();
}
```

### MessageProcessorRegistry
Registry that manages message processors and routes messages to appropriate handlers based on message type.

## Usage

### 1. Implement a Message Processor

```java
@Component
public class FHIRTransformationProcessor implements MessageProcessor {
    
    @Override
    public void process(QueueMessage message) {
        // Process the message
        String payload = message.getPayload();
        // ... transformation logic
    }
    
    @Override
    public String getMessageType() {
        return "FHIR_TRANSFORMATION";
    }
}
```

### 2. Send Messages

```java
@Service
public class MyService {
    
    private final MessageProducerService messageProducer;
    
    public void processData(String data) {
        QueueMessage message = new QueueMessage(data, "FHIR_TRANSFORMATION");
        messageProducer.sendMessage(message);
    }
}
```

### 3. Configure RabbitMQ Connection

Add to `application.yml`:
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

Or use environment variables:
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

## Retry Logic

The system implements exponential backoff for retries:
- Retry 1: 1 second delay
- Retry 2: 2 seconds delay
- Retry 3: 4 seconds delay
- Retry 4: 8 seconds delay
- Retry 5: 16 seconds delay
- Retry 6: 32 seconds delay

After max retries (default: 5), messages are sent to the dead letter queue.

## Error Handling

1. **Transient Errors**: Automatically retried with exponential backoff
2. **Permanent Errors**: Sent to DLQ after max retries exceeded
3. **Processing Errors**: Logged with full context for debugging
4. **DLQ Monitoring**: Dead letter queue listener logs all failed messages

## Requirements Satisfied

This implementation satisfies the following requirements:
- **Requirement 5.1**: Queue failed messages for retry processing
- **Requirement 5.2**: Buffer outgoing messages when connectivity is lost
- **Requirement 5.6**: Dead letter queue for messages exceeding retry limits

## Testing

Run tests with:
```bash
mvn test -pl smart-bridge-core -Dtest="*Queue*Test"
```

Tests cover:
- Message creation and retry logic
- Producer service operations
- Consumer service processing and error handling
- Processor registry management
- Exponential backoff calculations
