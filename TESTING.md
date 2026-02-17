# Smart Bridge End-to-End Testing Guide

## Overview

Smart Bridge now supports:
1. **Automatic incremental sync** - Runs every 5 minutes (configurable)
2. **Manual bulk sync** - Pull all clients from UCS
3. **Manual incremental sync** - Pull only new clients since last sync

## How It Works

### Sync Mechanism
- Uses UCS endpoint: `GET /rest/client/getAll?serverVersion=X`
- Tracks `serverVersion` in `data/server-version.txt` to enable incremental syncs
- On first run (or bulk sync), starts from `serverVersion=0`
- Each sync updates the stored `serverVersion` to the highest value received

### Automatic Scheduling
- Runs every 5 minutes by default (300,000 ms)
- Configure via `SYNC_INTERVAL_MS` environment variable
- Disable via `SYNC_ENABLED=false`

## Testing Steps

### 1. Start the Application

```bash
# Kill existing process if running
pkill -f smart-bridge-application

# Start fresh
mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev > /tmp/smartbridge.log 2>&1 &
echo $!  # Note the PID
```

### 2. Initial Bulk Sync

Trigger a bulk sync to pull all existing UCS clients:

```bash
curl -X POST http://localhost:8080/smart-bridge/api/sync/bulk
```

**Expected Response:**
```
Bulk sync started
```

**Check logs:**
```bash
tail -f /tmp/smartbridge.log | grep -i sync
```

You should see:
- "Starting bulk UCS to FHIR sync from scratch"
- "Fetching clients from UCS: http://localhost:8081/ucs/rest/client/getAll?serverVersion=0"
- "Received X clients from UCS"
- "Sync complete: X success, 0 errors, serverVersion=Y"

### 3. Verify FHIR Server

Check that patients were created in FHIR:

```bash
curl http://localhost:8082/fhir/Patient
```

You should see Patient resources with identifiers matching UCS `baseEntityId`.

### 4. Test Incremental Sync

Add a new client in UCS, then trigger incremental sync:

```bash
curl -X POST http://localhost:8080/smart-bridge/api/sync/incremental
```

This will only fetch clients with `serverVersion > last_saved_version`.

### 5. Verify Automatic Sync

Wait 5 minutes (or your configured interval) and check logs:

```bash
tail -f /tmp/smartbridge.log | grep "incremental UCS to FHIR sync"
```

You should see automatic sync runs every 5 minutes.

### 6. Check Sync State

The current `serverVersion` is stored in:

```bash
cat data/server-version.txt
```

### 7. Reset Sync State

To force a full re-sync from scratch:

```bash
rm data/server-version.txt
curl -X POST http://localhost:8080/smart-bridge/api/sync/bulk
```

## Monitoring

### Health Check
```bash
curl http://localhost:8080/smart-bridge/actuator/health
```

### Metrics
```bash
# All metrics
curl http://localhost:8080/smart-bridge/actuator/metrics

# Specific metric (e.g., transformation timer)
curl http://localhost:8080/smart-bridge/actuator/metrics/transformation.timer
```

### Prometheus Metrics
```bash
curl http://localhost:8080/smart-bridge/actuator/prometheus
```

## Configuration

### Environment Variables

```bash
# UCS API URL
export UCS_API_URL=http://localhost:8081/ucs

# FHIR Server URL
export FHIR_SERVER_URL=http://localhost:8082/fhir

# Sync interval (milliseconds)
export SYNC_INTERVAL_MS=300000  # 5 minutes

# Enable/disable automatic sync
export SYNC_ENABLED=true
```

### Application Properties

Edit `smart-bridge-application/src/main/resources/application.yml`:

```yaml
smartbridge:
  sync:
    interval-ms: 300000  # 5 minutes
    enabled: true
```

## Troubleshooting

### Sync Not Running
1. Check if scheduling is enabled: `@EnableScheduling` in `SmartBridgeApplication`
2. Check `SYNC_ENABLED` environment variable
3. Check logs for errors

### Clients Not Syncing
1. Verify UCS endpoint: `curl http://localhost:8081/ucs/rest/client/getAll?serverVersion=0`
2. Check UCS response format matches expected structure
3. Check transformation errors in logs

### FHIR Server Errors
1. Verify FHIR server is running: `curl http://localhost:8082/fhir/metadata`
2. Check FHIR client configuration in `application.yml`
3. Check circuit breaker status in logs

## Next Steps: Reverse Sync (FHIR → UCS)

To test reverse sync (FHIR Observations back to UCS):

1. Create an Observation in FHIR linked to a Patient
2. The FHIR change detection service should pick it up
3. It will be transformed and sent back to UCS

This requires:
- FHIR webhook configuration (or polling)
- UCS API endpoint to receive observations
- Reverse transformation logic (already implemented)

Let me know if you need help setting up the reverse sync flow!
