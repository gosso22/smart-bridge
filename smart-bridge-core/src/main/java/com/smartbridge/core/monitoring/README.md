# Smart Bridge Monitoring and Metrics

This package provides comprehensive monitoring and metrics collection for the Smart Bridge interoperability solution using Micrometer with Prometheus integration.

## Components

### MetricsService

The `MetricsService` provides methods to record various metrics throughout the application:

- **Transformation Metrics**: Track transformation operations with timing and success/failure counts
- **Mediator Metrics**: Monitor mediator operations and performance
- **FHIR Operations**: Count FHIR resource operations by type and status
- **UCS Operations**: Track UCS API calls by endpoint and method
- **Audit Logs**: Count audit log entries by event type
- **Security Events**: Monitor security events by type and severity
- **Queue Operations**: Track message queue operations
- **Circuit Breaker**: Monitor circuit breaker state changes

### Health Indicators

The monitoring package includes several health indicators:

- **FHIRHealthIndicator**: Checks HAPI FHIR server connectivity
- **UCSHealthIndicator**: Monitors UCS system availability
- **MessageQueueHealthIndicator**: Verifies RabbitMQ connectivity
- **TransformationHealthIndicator**: Monitors internal service health and memory usage

### HealthCheckController

REST endpoints for health checks:

- `GET /api/health` - Overall system health status
- `GET /api/health/live` - Liveness probe (service is running)
- `GET /api/health/ready` - Readiness probe (service is ready to accept traffic)

## Configuration

### Application Properties

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  prometheus:
    metrics:
      export:
        enabled: true
```

### Custom Configuration

Enable/disable monitoring:

```yaml
smart-bridge:
  monitoring:
    metrics-enabled: true
    audit-enabled: true
```

## Available Metrics

### Transformation Metrics

- `smart_bridge_transformation_duration` - Timer for transformation operations
- `smart_bridge_transformation_success_total` - Counter for successful transformations
- `smart_bridge_transformation_error_total` - Counter for failed transformations
- `smart_bridge_active_transformations` - Gauge for currently active transformations
- `smart_bridge_transformation_throughput` - Gauge for items per second

### Mediator Metrics

- `smart_bridge_mediator_duration` - Timer for mediator operations
- `smart_bridge_mediator_operations_total` - Counter for mediator operations

### System Metrics

- `smart_bridge_fhir_operations_total` - Counter for FHIR operations
- `smart_bridge_ucs_operations_total` - Counter for UCS operations
- `smart_bridge_queue_operations_total` - Counter for queue operations
- `smart_bridge_queue_depth` - Gauge for current queue depth

### Security and Audit Metrics

- `smart_bridge_audit_logs_total` - Counter for audit log entries
- `smart_bridge_security_events_total` - Counter for security events

### Resilience Metrics

- `smart_bridge_circuit_breaker_state_changes_total` - Counter for circuit breaker state changes

## Usage Examples

### Recording a Transformation

```java
@Autowired
private MetricsService metricsService;

public FHIRResource transform(UCSClient client) throws Exception {
    return metricsService.recordTransformation("ucs", "fhir", () -> {
        // Perform transformation
        return transformedResource;
    });
}
```

### Recording FHIR Operations

```java
metricsService.recordFHIROperation("Patient", "POST", true);
```

### Recording Security Events

```java
metricsService.recordSecurityEvent("authentication_failure", "high");
```

### Recording Throughput

```java
metricsService.recordThroughput(100, Duration.ofSeconds(10));
```

## Prometheus Integration

### Accessing Metrics

Metrics are exposed at: `http://localhost:8080/smart-bridge/actuator/prometheus`

### Sample Prometheus Configuration

```yaml
scrape_configs:
  - job_name: 'smart-bridge'
    metrics_path: '/smart-bridge/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

## Health Check Endpoints

### Overall Health

```bash
curl http://localhost:8080/smart-bridge/api/health
```

Response:
```json
{
  "overall": "UP",
  "FHIR": {
    "status": "UP",
    "details": {
      "fhir-server": "http://localhost:8082/fhir",
      "response-time-ms": 45,
      "status": "reachable"
    }
  },
  "UCS": {
    "status": "UP",
    "details": {
      "ucs-api": "http://localhost:8081/ucs",
      "response-time-ms": 32,
      "status": "reachable"
    }
  }
}
```

### Liveness Probe

```bash
curl http://localhost:8080/smart-bridge/api/health/live
```

### Readiness Probe

```bash
curl http://localhost:8080/smart-bridge/api/health/ready
```

## Kubernetes Integration

### Liveness and Readiness Probes

```yaml
livenessProbe:
  httpGet:
    path: /smart-bridge/api/health/live
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /smart-bridge/api/health/ready
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

## Performance Considerations

- Metrics collection has minimal overhead (< 1ms per operation)
- Health checks are cached for 10 seconds to reduce load
- Prometheus scraping recommended at 15-30 second intervals
- All metrics are tagged for easy filtering and aggregation

## Alerting

### Recommended Alerts

1. **High Error Rate**: `rate(smart_bridge_transformation_error_total[5m]) > 0.1`
2. **High Latency**: `histogram_quantile(0.95, smart_bridge_transformation_duration) > 5`
3. **Service Down**: `up{job="smart-bridge"} == 0`
4. **High Memory Usage**: `smart_bridge_transformation_status == "critical"`
5. **Circuit Breaker Open**: `smart_bridge_circuit_breaker_state_changes_total{state="open"} > 0`

## Troubleshooting

### Metrics Not Appearing

1. Verify `smart-bridge.monitoring.metrics-enabled=true`
2. Check that Micrometer dependencies are present
3. Ensure Prometheus endpoint is exposed in management configuration

### Health Checks Failing

1. Verify external service URLs are correct
2. Check network connectivity to FHIR server and UCS system
3. Review application logs for detailed error messages

### High Memory Usage

1. Monitor `smart_bridge_active_transformations` gauge
2. Check for memory leaks in transformation logic
3. Consider increasing JVM heap size or implementing backpressure
