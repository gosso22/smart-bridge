# OpenHIM Mediator Registration Guide (v8.5.1)

## Overview

Smart Bridge uses OpenHIM v8.5.1 as the central routing and transformation engine. This guide covers the updated UI and configuration approach for OpenHIM v8.5.1, which differs significantly from earlier versions.

## Prerequisites

- OpenHIM Core v8.5.1 running and accessible
- OpenHIM Console v1.15.0 configured for administration
- MongoDB 6.0 for OpenHIM data storage
- Network connectivity between Smart Bridge and OpenHIM Core
- Admin credentials (default: root@openhim.org / openhim-password)

## OpenHIM Core Setup

### 1. Docker Compose Setup (Recommended)

The project includes a `docker-compose.yml` with OpenHIM v8.5.1:

```bash
# Start all services
docker-compose up -d

# Check OpenHIM Core status
docker logs smart-bridge-openhim-core

# Verify heartbeat (note: may return empty initially)
curl http://localhost:8080/heartbeat
```

### 2. Access OpenHIM Console

1. Navigate to `http://localhost:9000`
2. Login with default credentials:
   - Username: `root@openhim.org`
   - Password: `openhim-password`
3. Change the default password immediately in production

### 3. Create a Channel for Smart Bridge

In OpenHIM Console v1.15.0, the UI has changed significantly:

#### Navigate to Channels
1. Click "Channels" in the left sidebar
2. Click "+ Channel" to create a new channel

#### Basic Info Tab
- **Channel Name**: `Smart Bridge UCS to FHIR`
- **Channel Type**: HTTP
- **Status**: Enabled

#### Request Matching Tab
- **URL Pattern**: `/smart-bridge/api/sync/ingest`
- **Allowed Methods**: POST, PUT
- **Authentication**: None (or configure as needed)
- **Whitelist**: Add allowed client IPs or leave empty for development

#### Routes Tab (Updated UI)

Click "Set Route" and configure:

- **Route Name**: `Smart Bridge Mediator`
- **Route Type**: HTTP (selected by default)
- **Route Secured**: No (for development) / Yes (for production with TLS)
- **Primary Route**: Yes (toggle to green)
- **Status**: Enabled (toggle to green)
- **Host**: `host.docker.internal` (if Smart Bridge runs on host) or `smart-bridge-app` (if containerized)
- **Port**: `8083` (Smart Bridge application port)
- **Route Path**: `/smart-bridge/api/sync/ingest`
- **Route Path Transform**: Leave empty (or use `s/from/to/g` for path rewriting)
- **Basic Authentication**: Leave empty unless Smart Bridge requires it
- **Forward existing Authorization header**: Yes (if needed)

Click "Set Route" button, then "Save changes"

#### Data Control Tab
- **Auto Retry**: Configure retry policy if needed
- **Store Request Body**: Yes (for debugging)
- **Store Response Body**: Yes (for debugging)

#### User Access Tab
- Add users/groups that can access this channel
- For development, you can leave this empty

#### Alerts Tab
- Configure alerts for failed transactions if needed

#### Logs Tab
- View transaction logs for this channel

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

## Channel Configuration Examples

### Example 1: UCS-to-FHIR Ingestion Channel

**Basic Info:**
- Name: `Smart Bridge UCS to FHIR`
- Type: HTTP
- Status: Enabled

**Request Matching:**
- URL Pattern: `/smart-bridge/api/sync/ingest`
- Methods: POST, PUT

**Routes:**
- Route Name: `Smart Bridge Mediator`
- Host: `host.docker.internal` (or your Smart Bridge hostname)
- Port: `8083`
- Path: `/smart-bridge/api/sync/ingest`
- Primary: Yes
- Status: Enabled

### Example 2: FHIR Webhook Channel

**Basic Info:**
- Name: `FHIR Webhook to Smart Bridge`
- Type: HTTP
- Status: Enabled

**Request Matching:**
- URL Pattern: `/smart-bridge/fhir/webhook`
- Methods: POST

**Routes:**
- Route Name: `Smart Bridge FHIR Handler`
- Host: `host.docker.internal`
- Port: `8083`
- Path: `/smart-bridge/fhir/webhook`
- Primary: Yes
- Status: Enabled

## Testing Your Channel

After creating a channel, test it:

```bash
# Test the channel endpoint
curl -X POST http://localhost:8080/smart-bridge/api/sync/ingest \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}'

# Check transaction logs in OpenHIM Console
# Navigate to: Transactions > View recent transactions
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

### OpenHIM Core Not Starting

**Symptom**: MongoDB connection timeout errors

```
MongooseError: Operation `agendaJobs.createIndex()` buffering timed out after 10000ms
```

**Solution**: Ensure MongoDB is fully started before OpenHIM Core:
1. Check MongoDB is healthy: `docker logs smart-bridge-mongo`
2. Restart OpenHIM: `docker-compose restart openhim-core`
3. If port 27017 is in use, stop local MongoDB: `sudo systemctl stop mongod`

### Empty Reply from Heartbeat

**Symptom**: `curl http://localhost:8080/heartbeat` returns empty reply

**Possible Causes**:
1. OpenHIM Core still initializing (wait 30-60 seconds)
2. MongoDB connection issues (check logs)
3. Port conflict on 8080

**Check Status**:
```bash
# View OpenHIM logs
docker logs smart-bridge-openhim-core --tail 50

# Check if OpenHIM is running
docker ps | grep openhim

# Verify MongoDB connection
docker exec smart-bridge-mongo mongosh --eval "db.adminCommand('ping')"
```

### Channel Not Routing Requests

1. Verify channel is enabled in OpenHIM Console
2. Check URL pattern matches your request path
3. Ensure route host/port are correct
4. Review transaction logs in OpenHIM Console for errors
5. Verify Smart Bridge application is running and accessible

### Mediator Registration Fails

1. Verify OpenHIM Core URL is correct and reachable:
   ```bash
   curl http://localhost:8080/heartbeat
   ```
2. Check credentials in `.env` file
3. Review application logs for detailed error messages
4. Ensure network connectivity between Smart Bridge and OpenHIM

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

## Key Differences from Older OpenHIM Versions

### UI Changes in v8.5.1
- Tabbed interface for channel configuration (Basic Info, Request Matching, Routes, etc.)
- "Set Route" button to add/edit routes instead of inline forms
- Toggle buttons for Primary Route and Status instead of dropdowns
- Route Path Transform field for URL rewriting
- Simplified authentication configuration

### Configuration Changes
- MongoDB connection uses `mongo_url` environment variable
- Heartbeat endpoint may return empty initially (this is normal)
- Default ports: 8080 (HTTP), 5000 (HTTPS API), 5001 (HTTPS)
- Console runs on port 9000 (was 9000 in older versions too)

### Best Practices for v8.5.1
- Use `host.docker.internal` for Docker-to-host communication
- Enable "Store Request/Response Body" during development
- Configure proper authentication for production
- Use route path transforms for URL rewriting instead of proxy rewrites
- Monitor transaction logs regularly for routing issues
