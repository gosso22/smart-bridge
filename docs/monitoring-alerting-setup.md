# Monitoring and Alerting Setup Guide

## Overview

Smart Bridge uses Micrometer with Prometheus for metrics collection, Spring Boot Actuator for health checks, and a custom `AlertingService` for security monitoring and administrator notifications. This guide covers setup and configuration of all monitoring components.

## Architecture

```
Smart Bridge Application
  ├── MetricsService (Micrometer counters, timers, gauges)
  ├── HealthCheckController (/api/health, /api/health/live, /api/health/ready)
  ├── Health Indicators
  │   ├── FHIRHealthIndicator (FHIR server connectivity)
  │   ├── MessageQueueHealthIndicator (RabbitMQ connectivity)
  │   └── TransformationHealthIndicator (memory/resource monitoring)
  ├── AlertingService (system alerts + security monitoring)
  └── Spring Boot Actuator (/actuator/health, /actuator/prometheus)
         └── Prometheus scrapes metrics
              └── Grafana dashboards (optional)
```

## Configuration

### Enable Monitoring

```bash
METRICS_ENABLED=true
AUDIT_ENABLED=true
```

### Actuator Endpoints

Configured in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
  prometheus:
    metrics:
      export:
        enabled: true
```

### Available Endpoints

| Endpoint | Description |
|----------|-------------|
| `/smart-bridge/actuator/health` | Overall application health |
| `/smart-bridge/actuator/health/liveness` | Kubernetes liveness probe |
| `/smart-bridge/actuator/health/readiness` | Kubernetes readiness probe |
| `/smart-bridge/actuator/prometheus` | Prometheus metrics endpoint |
| `/smart-bridge/actuator/metrics` | Micrometer metrics listing |
| `/smart-bridge/actuator/info` | Application info |
| `/smart-bridge/api/health` | Custom health check (all services) |
| `/smart-bridge/api/health/live` | Custom liveness probe |
| `/smart-bridge/api/health/ready` | Custom readiness probe |
| `/smart-bridge/mediator/health` | Mediator health status |

## Prometheus Setup

### 1. Configure Prometheus Scraping

Add to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'smart-bridge'
    metrics_path: '/smart-bridge/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['smart-bridge-host:8080']
        labels:
          application: 'smart-bridge'
          environment: 'production'
```

### 2. Available Metrics

#### Transformation Metrics
| Metric | Type | Description |
|--------|------|-------------|
| `smart_bridge_transformation_duration` | Timer | Transformation operation duration |
| `smart_bridge_transformation_success_total` | Counter | Successful transformations |
| `smart_bridge_transformation_error_total` | Counter | Failed transformations |
| `smart_bridge_transformation_throughput` | Gauge | Items per second |

#### Mediator Metrics
| Metric | Type | Description |
|--------|------|-------------|
| `smart_bridge_mediator_duration` | Timer | Mediator operation duration |
| `smart_bridge_mediator_operations_total` | Counter | Mediator operations by type |

#### System Metrics
| Metric | Type | Description |
|--------|------|-------------|
| `smart_bridge_fhir_operations_total` | Counter | FHIR operations by resource/method |
| `smart_bridge_ucs_operations_total` | Counter | UCS API operations by endpoint |
| `smart_bridge_queue_operations_total` | Counter | Message queue operations |

#### Security and Audit Metrics
| Metric | Type | Description |
|--------|------|-------------|
| `smart_bridge_audit_logs_total` | Counter | Audit log entries by event type |
| `smart_bridge_security_events_total` | Counter | Security events by type/severity |
| `smart_bridge_circuit_breaker_state_changes_total` | Counter | Circuit breaker state changes |

#### HTTP SLO Metrics

Configured with histogram buckets at 100ms, 200ms, 500ms, 1s, and 5s:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms,200ms,500ms,1s,5s
```

## Health Indicators

### FHIR Health Indicator

Checks FHIR server connectivity by calling the `/metadata` endpoint. Reports:
- Server URL
- Response time in milliseconds
- Reachability status

### Message Queue Health Indicator

Verifies RabbitMQ connectivity by attempting to open a channel. Reports:
- Queue type (RabbitMQ)
- Response time
- Connection status

### Transformation Health Indicator

Monitors JVM memory usage with thresholds:
- Below 80%: healthy
- 80-90%: warning (still UP but logged)
- Above 90%: critical (DOWN)

Reports memory used/max in MB and usage percentage.

## Alerting Service

### System Alerts

The `AlertingService` monitors and alerts on:

| Alert Type | Severity | Trigger |
|-----------|----------|---------|
| Transformation failure | MEDIUM | Data transformation operation fails |
| FHIR server unavailable | HIGH | Cannot reach FHIR server |
| UCS API unavailable | HIGH | Cannot reach UCS API |
| Circuit breaker open | MEDIUM | Circuit breaker trips for a service |
| Queue overflow | MEDIUM | Message queue approaching capacity |
| Dead letter queue | HIGH | Message exceeds retry limits |
| Performance degradation | MEDIUM | Operation exceeds time threshold |

### Security Alerts

| Alert Type | Severity | Trigger |
|-----------|----------|---------|
| Authentication failure | HIGH | 3+ failed login attempts |
| Activity blocked | CRITICAL | 5+ failed login attempts (auto-block) |
| Authorization violation | MEDIUM | 3+ unauthorized access attempts |
| Suspicious data access | HIGH | 10+ unusual access patterns |
| Encryption failure | CRITICAL | Encryption/decryption error |
| Audit tampering | CRITICAL | Audit trail integrity violation |
| TLS violation | HIGH | Insecure connection attempt |

### Alert Throttling

Alerts are throttled to prevent storms:
- Window: 1 minute per unique alert key
- Maximum: 5 alerts per window per key

## Logging

### Log Files

| File | Content | Retention |
|------|---------|-----------|
| `logs/smart-bridge.log` | Application logs | 30 days, 3GB cap |
| `logs/smart-bridge-audit.log` | Compliance audit trail | 365 days, 10GB cap |
| `logs/smart-bridge-security.log` | Security events | 365 days, 5GB cap |

### Log Levels by Profile

| Logger | Dev | Prod |
|--------|-----|------|
| Root | DEBUG | WARN |
| `com.smartbridge` | DEBUG | INFO |
| `ca.uhn.fhir` | INFO | ERROR |
| `org.springframework` | INFO | WARN |

### Dedicated Loggers

| Logger Name | Destination | Purpose |
|-------------|-------------|---------|
| `SECURITY_ALERT` | security.log + console | Security alert events |
| `SYSTEM_ALERT` | application.log + console | System alert events |
| `ADMIN_NOTIFICATION` | application.log + security.log + console | Admin notifications |
| `com.smartbridge.audit` | audit.log + console | Audit events |
| `com.smartbridge.security` | security.log + console | Security events |

## Recommended Prometheus Alert Rules

```yaml
groups:
  - name: smart-bridge-alerts
    rules:
      - alert: HighTransformationErrorRate
        expr: rate(smart_bridge_transformation_error_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transformation error rate"

      - alert: TransformationLatencyHigh
        expr: histogram_quantile(0.95, smart_bridge_transformation_duration) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "95th percentile transformation latency exceeds 5s"

      - alert: SmartBridgeDown
        expr: up{job="smart-bridge"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Smart Bridge application is down"

      - alert: CircuitBreakerOpen
        expr: increase(smart_bridge_circuit_breaker_state_changes_total{state="open"}[5m]) > 0
        for: 0m
        labels:
          severity: warning
        annotations:
          summary: "Circuit breaker opened for {{ $labels.service }}"

      - alert: HighSecurityEventRate
        expr: rate(smart_bridge_security_events_total{severity="high"}[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High rate of security events detected"
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
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /smart-bridge/api/health/ready
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 3
```

### Readiness Criteria

The readiness probe checks these critical services:
- FHIR server connectivity
- RabbitMQ connectivity
- Transformation service health (memory)

The application reports NOT_READY if any critical service is down.

## Extending Notifications

The `AlertingService.notifyAdministrators()` method can be extended to support:
- Email via SMTP
- SMS via gateway
- Slack webhooks
- PagerDuty incidents
- Custom webhook endpoints

See `ALERTING.md` in the monitoring package for integration examples.
