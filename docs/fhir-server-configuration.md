# HAPI FHIR Server Configuration Guide

## Overview

Smart Bridge connects to a HAPI FHIR R4 server as the central standardized datastore. The system performs CRUD operations on Patient, Observation, Task, and MedicalRequest resources, and uses change detection mechanisms (polling, subscriptions, webhooks) for reverse sync.

## Prerequisites

- HAPI FHIR Server 6.8.x running with R4 support
- Network connectivity from Smart Bridge to the FHIR server
- Appropriate authentication credentials configured

## HAPI FHIR Server Setup

### 1. Deploy HAPI FHIR Server

```bash
# Docker-based setup
docker run -d --name hapi-fhir \
  -p 8082:8080 \
  -e hapi.fhir.default_encoding=json \
  -e hapi.fhir.fhir_version=R4 \
  -e hapi.fhir.subscription.resthook_enabled=true \
  -e hapi.fhir.subscription.websocket_enabled=false \
  hapiproject/hapi:v6.8.0

# Verify server is running
curl http://localhost:8082/fhir/metadata
```

### 2. Enable Subscriptions

For real-time change detection, enable FHIR R4 subscriptions in the HAPI FHIR server configuration:

```yaml
# hapi.properties or application.yaml for HAPI FHIR
hapi:
  fhir:
    fhir_version: R4
    subscription:
      resthook_enabled: true
      websocket_enabled: false
    allow_external_references: true
```

### 3. Configure Authentication

If the FHIR server requires authentication, configure one of:

- Basic authentication (username/password)
- Bearer token authentication
- No authentication (development only)

## Smart Bridge Configuration

### Environment Variables

```bash
# FHIR server connection
FHIR_SERVER_URL=http://localhost:8082/fhir
FHIR_TIMEOUT=30000

# FHIR authentication (in FHIRClientConfig)
# Set in application.yml or environment
fhir.server.url=http://localhost:8082/fhir
fhir.auth.type=none          # Options: none, basic, bearer
fhir.auth.username=           # For basic auth
fhir.auth.password=           # For basic auth
fhir.auth.token=              # For bearer auth
```

### Application Properties

```yaml
smartbridge:
  fhir:
    server-url: ${FHIR_SERVER_URL:http://localhost:8082/fhir}
    timeout: ${FHIR_TIMEOUT:30000}
```

### FHIRClientConfig Bean

The `FHIRClientConfig` class creates a `FHIRClientService` bean conditionally when `fhir.server.url` is set. It auto-configures authentication based on `fhir.auth.type`.

## Supported FHIR Operations

### Patient Resources

| Operation | HTTP Method | Endpoint | Description |
|-----------|-------------|----------|-------------|
| Create | POST | `/fhir/Patient` | Create new patient from UCS data |
| Read | GET | `/fhir/Patient/{id}` | Retrieve patient by ID |
| Update | PUT | `/fhir/Patient/{id}` | Update existing patient |
| Search | GET | `/fhir/Patient?_lastUpdated=gt{ts}` | Find recently changed patients |

### Observation Resources

| Operation | HTTP Method | Endpoint | Description |
|-----------|-------------|----------|-------------|
| Create | POST | `/fhir/Observation` | Create clinical observation |
| Search | GET | `/fhir/Observation?patient={id}&_lastUpdated=gt{ts}` | Find patient observations |

### Task and MedicalRequest Resources

| Operation | HTTP Method | Endpoint | Description |
|-----------|-------------|----------|-------------|
| Create | POST | `/fhir/Task` | Create workflow task |
| Create | POST | `/fhir/MedicationRequest` | Create medication request |

## Change Detection

Smart Bridge supports three change detection mechanisms:

### 1. Polling (Default)

Periodically queries the FHIR server using `_lastUpdated` parameter:

```
GET /fhir/Patient?_lastUpdated=gt2026-02-11T00:00:00Z
GET /fhir/Observation?patient={id}&_lastUpdated=gt2026-02-11T00:00:00Z
```

### 2. FHIR R4 Subscriptions

Creates subscription resources on the FHIR server for real-time notifications:

```json
{
  "resourceType": "Subscription",
  "status": "requested",
  "criteria": "Patient?_lastUpdated=gt2026-02-11T00:00:00Z",
  "channel": {
    "type": "rest-hook",
    "endpoint": "http://smart-bridge-host:8080/smart-bridge/fhir/webhook",
    "payload": "application/fhir+json"
  }
}
```

### 3. Webhook Integration

The `FHIRWebhookController` receives HTTP callbacks at `/fhir/webhook` for immediate change notification from the FHIR server.

## Identifier Systems

Smart Bridge uses these identifier systems when creating FHIR resources:

| UCS Field | FHIR Identifier System | Example |
|-----------|----------------------|---------|
| `identifiers.opensrp_id` | `http://moh.go.tz/identifier/opensrp-id` | `opensrp-12345` |
| `identifiers.national_id` | `http://moh.go.tz/identifier/national-id` | `TZ-NID-67890` |

## Health Monitoring

The `FHIRHealthIndicator` checks FHIR server connectivity by calling the `/metadata` endpoint:

```bash
# Check FHIR health via Smart Bridge
curl http://localhost:8080/smart-bridge/actuator/health
```

Response includes FHIR server status:
```json
{
  "components": {
    "fhir": {
      "status": "UP",
      "details": {
        "fhir-server": "http://localhost:8082/fhir",
        "response-time-ms": 45,
        "status": "reachable"
      }
    }
  }
}
```

## Production Considerations

### TLS Configuration

For production, always use HTTPS for FHIR server connections:

```bash
FHIR_SERVER_URL=https://fhir-prod.internal/fhir
TLS_ENABLED=true
TLS_VERIFY_HOSTNAME=true
```

### Connection Timeouts

Adjust timeouts based on network conditions:

```bash
FHIR_TIMEOUT=30000  # 30 seconds default, increase for slow networks
```

### Circuit Breaker

The FHIR client is protected by a circuit breaker:
- Opens after 5 consecutive failures (configurable via `CIRCUIT_BREAKER_FAILURE_THRESHOLD`)
- Cooldown period: 30 seconds (`CIRCUIT_BREAKER_COOLDOWN`)
- Closes after 3 successful operations (`CIRCUIT_BREAKER_SUCCESS_THRESHOLD`)

## Troubleshooting

### Connection Refused

1. Verify FHIR server is running: `curl ${FHIR_SERVER_URL}/metadata`
2. Check firewall rules and network connectivity
3. Verify the URL includes the correct port and base path

### Authentication Errors

1. Confirm `fhir.auth.type` matches the server's auth configuration
2. Verify credentials are correct
3. Check token expiration for bearer auth

### Resource Validation Failures

1. Ensure FHIR server is running R4 (not STU3 or R5)
2. Check that HAPI FHIR library version (6.8.0) is compatible with the server
3. Review transformation logs for specific validation errors
