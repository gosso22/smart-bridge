# OpenHIM Mediator Registration Guide

## Overview

Smart Bridge uses OpenHIM as the central routing and transformation engine. Each mediator (UCS, FHIR) must be registered with OpenHIM Core before it can participate in data flows. Registration is handled automatically on application startup by `MediatorRegistrationService`, but manual steps are required for initial OpenHIM Core setup.

## Prerequisites

- OpenHIM Core 8.x running and accessible
- OpenHIM Console configured for administration
- Network connectivity between Smart Bridge and OpenHIM Core
- Admin credentials for OpenHIM Core API

## OpenHIM Core Setup

### 1. Install and Start OpenHIM Core

```bash
# Docker-based setup (recommended)
docker run -d --name openhim-core \
  -p 8080:8080 -p 5000:5000 -p 5001:5001 \
  -e mongo_url=mongodb://mongo/openhim \
  -e mongo_atnaUrl=mongodb://mongo/openhim \
  jembi/openhim-core:latest

# Verify it's running
curl -k https://localhost:8080/heartbeat
```

### 2. Configure OpenHIM Console

Access the OpenHIM Console at `https://localhost:9000` and log in with default credentials. Change the default password immediately.

### 3. Create API Client for Smart Bridge

In OpenHIM Console:
1. Navigate to Clients > Add Client
2. Set Client ID: `smart-bridge`
3. Set Client Name: `Smart Bridge Interoperability System`
4. Configure authentication (mutual TLS or basic auth)
5. Assign appropriate roles: `smart-bridge-mediator`

## Smart Bridge Configuration

### Environment Variables

Set these in your `.env` file or environment:

```bash
OPENHIM_CORE_URL=https://openhim.example.com:8080
OPENHIM_USERNAME=root@openhim.org
OPENHIM_PASSWORD=<secure-password>
OPENHIM_TRUST_SELF_SIGNED=false  # Set true only for development
```

### Application Properties

The `application.yml` maps these to Spring configuration:

```yaml
smartbridge:
  openhim:
    core-url: ${OPENHIM_CORE_URL:http://localhost:8080}
    api-username: ${OPENHIM_USERNAME:root@openhim.org}
    api-password: ${OPENHIM_PASSWORD:password}
    trust-self-signed: ${OPENHIM_TRUST_SELF_SIGNED:true}
```

## Automatic Registration

On application startup, `MediatorRegistrationService` performs:

1. Discovers all `MediatorService` beans (UCS Mediator, FHIR Mediator)
2. Calls `registerWithOpenHIM()` on each mediator
3. Sends a `MediatorRegistration` payload containing:
   - Mediator name and version
   - Description
   - Endpoint mappings
   - Default channel configuration
4. Logs success or failure for each registration

### Registration Flow

```
Application Start
  └─> ApplicationReadyEvent
       └─> MediatorRegistrationService.registerMediatorsOnStartup()
            ├─> UCSMediatorService.registerWithOpenHIM()
            │    └─> OpenHIMClient.registerMediator(registration)
            └─> FHIRMediatorService.registerWithOpenHIM()
                 └─> OpenHIMClient.registerMediator(registration)
```

## Heartbeat Management

After registration, the system sends periodic heartbeats to OpenHIM Core:

- Interval: every 30 seconds
- Initial delay: 60 seconds after startup
- Managed by: `MediatorRegistrationService.sendHeartbeat()`

If heartbeats fail, OpenHIM Core will mark the mediator as inactive. The system logs heartbeat failures at DEBUG level to avoid log noise.

## Channel Configuration

### UCS-to-FHIR Channel

Create in OpenHIM Console or via API:

```json
{
  "name": "UCS to FHIR Transformation",
  "urlPattern": "^/ucs/clients.*$",
  "type": "http",
  "methods": ["POST", "PUT"],
  "routes": [
    {
      "name": "Smart Bridge UCS Mediator",
      "host": "smart-bridge-host",
      "port": 8080,
      "path": "/smart-bridge/ucs/clients",
      "primary": true
    }
  ],
  "allow": ["smart-bridge-mediator"],
  "authType": "private"
}
```

### FHIR-to-UCS Channel (Reverse Sync)

```json
{
  "name": "FHIR to UCS Reverse Sync",
  "urlPattern": "^/fhir/webhook.*$",
  "type": "http",
  "methods": ["POST"],
  "routes": [
    {
      "name": "Smart Bridge FHIR Mediator",
      "host": "smart-bridge-host",
      "port": 8080,
      "path": "/smart-bridge/fhir/webhook",
      "primary": true
    }
  ],
  "allow": ["smart-bridge-mediator"],
  "authType": "private"
}
```

## Health Check Endpoints

The mediator health controller exposes:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mediator/health` | GET | Health status of all registered mediators |
| `/mediator/config` | GET | Configuration of all registered mediators |

### Example Health Check Response

```json
{
  "status": "UP",
  "mediators": {
    "UCS Mediator": {
      "healthy": true,
      "message": "UCS API reachable",
      "responseTimeMs": 45
    },
    "FHIR Mediator": {
      "healthy": true,
      "message": "FHIR server reachable",
      "responseTimeMs": 32
    }
  },
  "timestamp": 1738800000000
}
```

## Troubleshooting

### Mediator Registration Fails

1. Verify OpenHIM Core URL is correct and reachable:
   ```bash
   curl -k ${OPENHIM_CORE_URL}/heartbeat
   ```
2. Check credentials are valid
3. Review application logs for detailed error messages
4. Ensure `OPENHIM_TRUST_SELF_SIGNED=true` if using self-signed certificates in dev

### Heartbeat Failures

1. Check network connectivity to OpenHIM Core
2. Verify the mediator was successfully registered first
3. Review OpenHIM Console for mediator status
4. Check firewall rules between Smart Bridge and OpenHIM Core

### No Mediators Found

If logs show "No mediators found to register":
1. Verify mediator beans are being created (check Spring context)
2. Ensure `smart-bridge-mediators` module is included in the build
3. Check for missing `@Service` annotations on mediator classes
