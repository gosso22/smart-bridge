# UCS System Integration Guide

## Overview

Smart Bridge integrates with the legacy Unified Client System (UCS) to enable bidirectional data synchronization with FHIR-based ecosystems. The UCS stores patient demographics, identifiers, and clinical metadata in a proprietary JSON format.

## Prerequisites

- UCS system accessible via REST API
- Authentication credentials (basic or token-based)
- Network connectivity from Smart Bridge to UCS API

## Smart Bridge Configuration

### Environment Variables

```bash
UCS_API_URL=http://localhost:8081/ucs
UCS_TIMEOUT=10000
UCS_AUTH_TYPE=token   # Options: token, basic
```

### Application Properties

```yaml
smartbridge:
  ucs:
    api-url: ${UCS_API_URL:http://localhost:8081/ucs}
    timeout: ${UCS_TIMEOUT:10000}
    auth-type: ${UCS_AUTH_TYPE:token}
```

## UCS Data Format

### Client Record Structure

The UCS system sends and receives client records in this JSON format:

```json
{
  "identifiers": {
    "opensrp_id": "string",
    "national_id": "string"
  },
  "demographics": {
    "firstName": "string",
    "lastName": "string",
    "gender": "M|F|O",
    "birthDate": "YYYY-MM-DD",
    "address": {
      "district": "string",
      "ward": "string",
      "village": "string"
    }
  },
  "clinicalData": {
    "observations": [],
    "medications": [],
    "procedures": []
  },
  "metadata": {
    "createdAt": "ISO8601",
    "updatedAt": "ISO8601",
    "source": "UCS"
  }
}
```

### Required Fields

All client records must include:
- `identifiers.opensrp_id` — primary identifier
- `demographics.firstName` and `demographics.lastName`
- `demographics.gender` — one of `M`, `F`, or `O`
- `demographics.birthDate` — in `YYYY-MM-DD` format

## Data Flow: UCS to FHIR (Ingestion)

1. UCS client record received via API or mediator
2. Validated against UCS JSON schema
3. Transformed to FHIR R4 Patient resource
4. FHIR resource validated against R4 specification
5. Stored in HAPI FHIR server
6. Confirmation returned to UCS

### Field Mapping

| UCS Field | FHIR Element | Notes |
|-----------|-------------|-------|
| `identifiers.opensrp_id` | `Patient.identifier[system="http://moh.go.tz/identifier/opensrp-id"]` | Primary identifier |
| `identifiers.national_id` | `Patient.identifier[system="http://moh.go.tz/identifier/national-id"]` | Secondary identifier |
| `demographics.firstName` | `Patient.name[0].given[0]` | |
| `demographics.lastName` | `Patient.name[0].family` | |
| `demographics.gender` | `Patient.gender` | Normalized: M→male, F→female, O→other |
| `demographics.birthDate` | `Patient.birthDate` | ISO date format |
| `demographics.address.district` | `Patient.address[0].district` | |
| `demographics.address.ward` | `Patient.address[0].city` | |
| `demographics.address.village` | `Patient.address[0].text` | |

### Gender Normalization

| UCS Value | FHIR Value |
|-----------|-----------|
| `M` | `male` |
| `F` | `female` |
| `O` | `other` |
| null/empty | `unknown` |

## Data Flow: FHIR to UCS (Reverse Sync)

1. FHIR resource change detected (polling/subscription/webhook)
2. Resource retrieved and validated
3. Transformed to UCS-compatible JSON format
4. Posted to UCS API
5. Response handled with error management
6. Audit logged

### Reverse Gender Mapping

| FHIR Value | UCS Value |
|-----------|-----------|
| `male` | `M` |
| `female` | `F` |
| `other` | `O` |
| `unknown` | null |

## Authentication

### Token-Based Authentication (Default)

```bash
UCS_AUTH_TYPE=token
```

The UCS mediator manages token lifecycle including acquisition and refresh. Token rotation is configured via:

```bash
TOKEN_ROTATION_INTERVAL=24        # Hours between rotations
TOKEN_GRACE_PERIOD=1              # Hours of overlap during rotation
TOKEN_AUTO_ROTATION=true
TOKEN_MONITORED_SYSTEMS=ucs-system,fhir-system,gothomis-system
```

### Basic Authentication

```bash
UCS_AUTH_TYPE=basic
```

Credentials are managed through the OpenHIM authentication layer.

## Resilience Configuration

The UCS API client (`ResilientUCSApiClient`) includes built-in resilience:

### Retry Policy

```bash
RETRY_MAX_ATTEMPTS=3              # Maximum retry attempts
RETRY_INITIAL_DELAY=1000          # Initial delay in ms
RETRY_MAX_DELAY=32000             # Maximum delay in ms
RETRY_BACKOFF_MULTIPLIER=2.0      # Exponential backoff multiplier
```

Retry sequence: 1s → 2s → 4s → 8s → 16s → 32s

### Circuit Breaker

```bash
CIRCUIT_BREAKER_FAILURE_THRESHOLD=5   # Failures before opening
CIRCUIT_BREAKER_COOLDOWN=30           # Seconds before half-open
CIRCUIT_BREAKER_SUCCESS_THRESHOLD=3   # Successes to close
```

### Network Monitoring

```bash
NETWORK_MONITOR_ENABLED=true
NETWORK_MONITOR_INTERVAL=30       # Check interval in seconds
NETWORK_MONITOR_TIMEOUT=5000      # Timeout in ms
```

## API Contract Preservation

Smart Bridge maintains exact API contract compatibility with UCS. The `LegacyApiCompatibilityService` and `CompatibilityInterceptor` ensure:

- No modifications required to existing UCS system interfaces
- All critical data fields preserved during interactions
- Request/response formats remain unchanged
- Error response formats match UCS expectations

## Message Queuing

Failed UCS operations are queued via RabbitMQ:

- Primary queue for normal processing
- Retry queue with exponential backoff
- Dead letter queue for messages exceeding retry limits

```bash
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
```

## Troubleshooting

### UCS API Unreachable

1. Verify URL: `curl ${UCS_API_URL}/health`
2. Check network connectivity and firewall rules
3. Verify authentication credentials
4. Review `AlertingService` logs for `alertUCSApiUnavailable` events

### Schema Validation Failures

1. Check that all required fields are present in the UCS record
2. Verify `gender` is one of `M`, `F`, `O`
3. Verify `birthDate` is in `YYYY-MM-DD` format
4. Review transformation logs for specific field-level errors

### Data Mapping Issues

1. Check transformation logs for mapping errors
2. Verify identifier formats match expected patterns
3. Review audit logs for transformation details
