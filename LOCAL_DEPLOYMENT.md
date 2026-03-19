# Smart Bridge Local Deployment Guide

This guide walks you through deploying Smart Bridge locally with all required components.

## Prerequisites

- Docker and Docker Compose installed
- Java 17 installed
- Maven installed
- Your UCS system URL and credentials

## Architecture Overview

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│ UCS System  │────▶│ Smart Bridge │────▶│ HAPI FHIR   │
│ (Cloud)     │◀────│  (Local)     │◀────│  (Local)    │
└─────────────┘     └──────────────┘     └─────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │   OpenHIM    │
                    │   (Local)    │
                    └──────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  RabbitMQ    │
                    │   (Local)    │
                    └──────────────┘
```

## Step 1: Create Docker Compose Setup

The project includes `docker-compose.yml` with OpenHIM v8.5.1. Key configuration:

```yaml
services:
  # HAPI FHIR Server
  hapi-fhir:
    image: hapiproject/hapi:v6.8.0
    ports:
      - "8082:8080"
    environment:
      - hapi.fhir.fhir_version=R4
      - hapi.fhir.subscription.resthook_enabled=true

  # MongoDB for OpenHIM
  mongo:
    image: mongo:6.0
    ports:
      - "27017:27017"
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      start_period: 20s

  # OpenHIM Core v8.5.1
  openhim-core:
    image: jembi/openhim-core:v8.5.1
    ports:
      - "8080:8080"  # HTTP
      - "5000:5000"  # HTTPS API
      - "5001:5001"  # HTTPS
    environment:
      - mongo_url=mongodb://mongo:27017/openhim
      - mongo_atnaUrl=mongodb://mongo:27017/openhim
      - NODE_ENV=development
      - mongo_bufferMaxEntries=0
    depends_on:
      mongo:
        condition: service_healthy
    restart: on-failure

  # OpenHIM Console v1.15.0
  openhim-console:
    image: jembi/openhim-console:1.15.0
    ports:
      - "9000:80"

  # RabbitMQ
  rabbitmq:
    image: rabbitmq:3.12-management
    ports:
      - "5672:5672"   # AMQP
      - "15672:15672" # Management UI
    environment:
      - RABBITMQ_DEFAULT_USER=smartbridge
      - RABBITMQ_DEFAULT_PASS=smartbridge123
```

## Step 2: Start Infrastructure Services

```bash
# If you have local MongoDB running on port 27017, stop it first
sudo systemctl stop mongod  # or: brew services stop mongodb-community

# Start all services
docker-compose up -d

# Wait for services to be healthy (2-3 minutes)
# OpenHIM may restart once or twice initially - this is normal
docker-compose ps

# Check OpenHIM logs (wait until you see "OpenHIM server started")
docker-compose logs -f openhim-core

# Once you see "OpenHIM server started", press Ctrl+C and continue
```

**Expected Output:**
```
smart-bridge-mongo         ... Up (healthy)
smart-bridge-openhim-core  ... Up (healthy)
smart-bridge-openhim-console ... Up
smart-bridge-rabbitmq      ... Up (healthy)
smart-bridge-hapi-fhir     ... Up (healthy)
```

**Note**: If OpenHIM shows "unhealthy" initially, wait 60 seconds and check again. The MongoDB connection may take time to stabilize.

## Step 3: Verify Services Are Running

```bash
# HAPI FHIR (should return capability statement)
curl http://localhost:8082/fhir/metadata

# OpenHIM Core (may return empty initially - this is normal for v8.5.1)
curl http://localhost:8080/heartbeat

# RabbitMQ Management UI
# Open browser: http://localhost:15672
# Login: smartbridge / smartbridge123

# OpenHIM Console (IMPORTANT: Use HTTP, not HTTPS)
# Open browser: http://localhost:9000
# Default login: root@openhim.org / openhim-password
```

**Troubleshooting:**
- If OpenHIM heartbeat returns empty, check logs: `docker logs smart-bridge-openhim-core --tail 50`
- Look for "OpenHIM server started" message
- If you see MongoDB connection errors, restart: `docker-compose restart openhim-core`

## Step 4: Configure OpenHIM v8.5.1

### 4.1 Access OpenHIM Console

1. Open browser: `http://localhost:9000` (HTTP, not HTTPS)
2. Login with default credentials:
   - Email: `root@openhim.org`
   - Password: `openhim-password`
3. **Important**: Change the default password immediately

### 4.2 Create a Client for Smart Bridge (Required)

You must create a client before creating channels, as OpenHIM v8.5.1 requires channels to specify allowed clients.

1. Navigate to **Clients** in the left sidebar
2. Click **+ Client**
3. Fill in:
   - **Client ID**: `smart-bridge-mediator`
   - **Client Name**: `Smart Bridge Mediator`
   - **Domain**: `localhost`
   - **Roles**: `admin`
4. Under **Authentication**, click **+ Basic Auth**:
   - Password: `smartbridge123`
5. Click **Save**

**Note**: Remember this client ID - you'll need it when creating channels.

### 4.3 Create Channels (Updated for v8.5.1 UI)

**Channel 1: UCS to FHIR Ingestion**

1. Navigate to **Channels** → Click **+ Channel**

2. **Basic Info** tab:
   - **Channel Name**: `UCS to FHIR Ingestion`
   - **Channel Type**: HTTP
   - **Status**: Enabled (toggle to green)

3. **Request Matching** tab:
   - **URL Pattern**: `/smart-bridge/api/sync/ingest`
   - **Allowed Methods**: Check POST and PUT
   - **Authentication**: None (for development)
   - **Whitelist**: Leave empty for development

4. **Routes** tab:
   - Click **Set Route** button
   - **Route Name**: `Smart Bridge Mediator`
   - **Route Type**: HTTP (selected by default)
   - **Route Secured**: No (toggle to green for development)
   - **Primary Route**: Yes (toggle to green)
   - **Status**: Enabled (toggle to green)
   - **Host**: `host.docker.internal` (for Docker-to-host communication)
   - **Port**: `8083`
   - **Route Path**: `/smart-bridge/api/sync/ingest`
   - **Route Path Transform**: Leave empty
   - **Basic Authentication Username**: Leave empty
   - **Basic Authentication Password**: Leave empty
   - **Forward existing Authorization header**: Yes (if needed)
   - Click **Set Route** button at bottom
   - Click **Save changes** button at bottom right

5. **Data Control** tab (optional):
   - **Store Request Body**: Yes (for debugging)
   - **Store Response Body**: Yes (for debugging)

6. **User Access** tab (Required):
   - **Which clients should be able to access this channel?**: Select `smart-bridge-mediator` (the client created in Step 4.2)
   - This field is required in OpenHIM v8.5.1

7. Click **Save changes** at the bottom

**Channel 2: FHIR to UCS Reverse Sync**

Repeat the same process with these values:
- **Channel Name**: `FHIR to UCS Reverse Sync`
- **URL Pattern**: `/smart-bridge/api/sync/reverse`
- **Route Path**: `/smart-bridge/api/sync/reverse`
- All other settings same as Channel 1

### 4.4 Verify Channels

1. Go to **Channels** in the left sidebar
2. You should see both channels listed with status "Enabled"
3. Click on a channel to view/edit configuration

## Step 5: Configure Smart Bridge Application

### 5.1 Update `.env` File

```bash
# Copy template if not already done
cp smart-bridge-application/src/main/resources/.env.template .env
```

Edit `.env` with your actual values:

```properties
# UCS Configuration (YOUR CLOUD-HOSTED SYSTEM)
UCS_API_URL=https://your-ucs-system.com/api
UCS_AUTH_TYPE=basic
UCS_USERNAME=your-ucs-username
UCS_PASSWORD=your-ucs-password

# FHIR Configuration (Local)
FHIR_SERVER_URL=http://localhost:8082/fhir
FHIR_AUTH_ENABLED=false

# OpenHIM Configuration (Local)
OPENHIM_CORE_URL=http://localhost:8080
OPENHIM_MEDIATOR_URN=urn:mediator:smart-bridge
OPENHIM_MEDIATOR_NAME=Smart Bridge Mediator
OPENHIM_API_USERNAME=root@openhim.org
OPENHIM_API_PASSWORD=openhim-password

# RabbitMQ Configuration (Local)
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=smartbridge
RABBITMQ_PASSWORD=smartbridge123
RABBITMQ_VHOST=/

# Security (Generate your own keys for production)
ENCRYPTION_KEY=changeme-32-character-key-here
TOKEN_SECRET=changeme-secret-key-here

# Monitoring
METRICS_ENABLED=true
AUDIT_ENABLED=true
```

### 5.2 Update Application Configuration

The application is already configured to read from `.env`. Verify `application.yml` has correct defaults:

```bash
cat smart-bridge-application/src/main/resources/application.yml
```

## Step 6: Build and Run Smart Bridge

```bash
# Build the application
mvn clean package -DskipTests

# Run with dev profile
mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev

# Or run the JAR directly
java -jar smart-bridge-application/target/smart-bridge-application-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## Step 7: Verify Smart Bridge is Running

```bash
# Health check
curl http://localhost:8083/smart-bridge/actuator/health

# Should return:
# {"status":"UP","components":{...}}

# Check metrics
curl http://localhost:8083/smart-bridge/actuator/metrics

# Check if mediator registered with OpenHIM
# Go to OpenHIM Console → Mediators
# You should see "Smart Bridge Mediator" listed
```

## Step 8: Test the Integration

### 8.1 Test UCS to FHIR Ingestion

Create a test file `test-ucs-client.json`:

```json
{
  "baseEntityId": "test-client-001",
  "identifiers": {
    "opensrp_id": "12345-67890"
  },
  "firstName": "John",
  "lastName": "Doe",
  "birthdate": "1990-01-15",
  "gender": "M",
  "addresses": [
    {
      "addressType": "usual",
      "cityVillage": "Nairobi",
      "country": "Kenya"
    }
  ]
}
```

Test the ingestion:

```bash
# Direct to Smart Bridge
curl -X POST http://localhost:8083/smart-bridge/api/sync/ingest \
  -H "Content-Type: application/json" \
  -d @test-ucs-client.json

# Or through OpenHIM
curl -X POST http://localhost:8080/ucs-to-fhir \
  -H "Content-Type: application/json" \
  -u smart-bridge-mediator:smartbridge123 \
  -d @test-ucs-client.json
```

### 8.2 Verify Patient Created in HAPI FHIR

```bash
# Search for the patient
curl "http://localhost:8082/fhir/Patient?identifier=12345-67890"

# Or open in browser
# http://localhost:8082/fhir/Patient
```

### 8.3 Test FHIR to UCS Reverse Sync

Update the patient in HAPI FHIR and trigger reverse sync:

```bash
# Get the patient ID from previous step
PATIENT_ID="<patient-id-from-fhir>"

# Trigger reverse sync
curl -X POST "http://localhost:8083/smart-bridge/api/sync/reverse?patientId=$PATIENT_ID"
```

### 8.4 Monitor in OpenHIM Console

1. Go to OpenHIM Console: `http://localhost:9000`
2. Navigate to **Transactions**
3. You should see transaction logs for your requests
4. Click on a transaction to see details (request, response, mediator info)

## Step 9: Monitor the System

### RabbitMQ Management

- URL: `http://localhost:15672`
- Login: `smartbridge / smartbridge123`
- Check queues: `smart-bridge.main`, `smart-bridge.retry`, `smart-bridge.dlq`

### Prometheus Metrics

```bash
# View all metrics
curl http://localhost:8083/smart-bridge/actuator/prometheus

# Specific metrics
curl http://localhost:8083/smart-bridge/actuator/metrics/transformation.duration
curl http://localhost:8083/smart-bridge/actuator/metrics/fhir.operations
```

### Application Logs

```bash
# Logs are written to logs/ directory
tail -f logs/smart-bridge.log
tail -f logs/audit.log
tail -f logs/security.log
```

## Step 10: Bulk Sync (Optional)

If you want to sync existing UCS clients to FHIR:

```bash
# Trigger bulk sync
curl -X POST "http://localhost:8083/smart-bridge/api/sync/bulk?batchSize=50"
```

## Troubleshooting

### Issue: Smart Bridge can't connect to UCS

```bash
# Test UCS connectivity
curl -u your-username:your-password https://your-ucs-system.com/api/health

# Check Smart Bridge logs
tail -f logs/smart-bridge.log | grep UCS
```

### Issue: HAPI FHIR not responding

```bash
# Check container status
docker-compose ps hapi-fhir

# Check logs
docker-compose logs hapi-fhir

# Restart if needed
docker-compose restart hapi-fhir
```

### Issue: OpenHIM mediator not registering

```bash
# Check OpenHIM Core logs
docker-compose logs openhim-core

# Verify OpenHIM credentials in .env
# Try manual registration via OpenHIM Console
```

### Issue: RabbitMQ connection failed

```bash
# Check RabbitMQ status
docker-compose ps rabbitmq

# Test connection
curl -u smartbridge:smartbridge123 http://localhost:15672/api/overview
```

## Stopping the System

```bash
# Stop Smart Bridge application
# Press Ctrl+C in the terminal running the app

# Stop Docker services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v
```

## Next Steps

1. **Configure FHIR Subscriptions**: Set up webhooks for real-time sync
2. **Add More UCS Clients**: Test with your actual UCS data
3. **Monitor Performance**: Use Prometheus metrics to track throughput
4. **Security Hardening**: Update passwords and encryption keys
5. **Production Deployment**: Follow `DEPLOYMENT.md` for production setup

## Quick Reference

| Service | URL | Credentials |
|---------|-----|-------------|
| Smart Bridge | http://localhost:8083/smart-bridge | - |
| HAPI FHIR | http://localhost:8082/fhir | - |
| OpenHIM Console | http://localhost:9000 | root@openhim.org / openhim-password |
| OpenHIM Core | http://localhost:8080 | - |
| RabbitMQ Management | http://localhost:15672 | smartbridge / smartbridge123 |
| Health Check | http://localhost:8083/smart-bridge/actuator/health | - |
| Metrics | http://localhost:8083/smart-bridge/actuator/metrics | - |

## Support

For issues or questions:
1. Check logs in `logs/` directory
2. Review OpenHIM transactions
3. Check RabbitMQ queues for failed messages
4. Review the main `README.md` and `DEPLOYMENT.md`
