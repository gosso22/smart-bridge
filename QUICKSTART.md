# Smart Bridge - Quick Start Guide

## 🚀 One-Command Setup

```bash
./deploy-local.sh
```

This script will:
- ✅ Check prerequisites (Docker, Java, Maven)
- ✅ Start all infrastructure services (HAPI FHIR, OpenHIM, RabbitMQ)
- ✅ Build Smart Bridge application
- ✅ Display service URLs and credentials

## 📝 Manual Setup (3 Steps)

### Step 1: Configure Your UCS System

Edit `.env` file:
```bash
UCS_API_URL=https://your-ucs-system.com/api
UCS_USERNAME=your-username
UCS_PASSWORD=your-password
```

### Step 2: Start Infrastructure

```bash
docker-compose up -d
```

Wait 2-3 minutes for services to start.

### Step 3: Run Smart Bridge

```bash
mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev
```

## 🧪 Test Your Setup

```bash
./test-local-deployment.sh
```

Or manually:
```bash
# Health check
curl http://localhost:8083/smart-bridge/actuator/health

# Test ingestion
curl -X POST http://localhost:8083/smart-bridge/api/sync/ingest \
  -H "Content-Type: application/json" \
  -d @test-data/test-ucs-client.json

# Check FHIR
curl http://localhost:8082/fhir/Patient
```

## 🌐 Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| **Smart Bridge** | http://localhost:8083/smart-bridge | - |
| **HAPI FHIR** | http://localhost:8082/fhir | - |
| **OpenHIM Console** | http://localhost:9000 | root@openhim.org / openhim-password |
| **RabbitMQ** | http://localhost:15672 | smartbridge / smartbridge123 |

## 🔧 OpenHIM Configuration

1. Open http://localhost:9000
2. Login: `root@openhim.org` / `openhim-password`
3. Create Client:
   - ID: `smart-bridge-mediator`
   - Password: `smartbridge123`
4. Create Channel:
   - Name: `UCS to FHIR Ingestion`
   - URL: `/ucs-to-fhir`
   - Route: `host.docker.internal:8083/smart-bridge/api/sync/ingest`

## 📊 Monitoring

```bash
# Application logs
tail -f logs/smart-bridge.log

# Health status
curl http://localhost:8083/smart-bridge/actuator/health

# Metrics
curl http://localhost:8083/smart-bridge/actuator/metrics

# RabbitMQ queues
open http://localhost:15672

# OpenHIM transactions
open http://localhost:9000
```

## 🛑 Stop Everything

```bash
# Stop Smart Bridge (Ctrl+C in terminal)

# Stop Docker services
docker-compose down

# Stop and remove all data
docker-compose down -v
```

## 🆘 Troubleshooting

### Services won't start
```bash
docker-compose ps
docker-compose logs <service-name>
```

### Can't connect to UCS
```bash
# Test UCS connectivity
curl -u username:password https://your-ucs-system.com/api/health
```

### Port already in use
```bash
# Check what's using the port
lsof -i :8083
lsof -i :8082

# Change ports in docker-compose.yml if needed
```

## 📚 Full Documentation

See `LOCAL_DEPLOYMENT.md` for detailed step-by-step instructions.

## 🎯 Common Tasks

### Sync a UCS Client to FHIR
```bash
curl -X POST http://localhost:8083/smart-bridge/api/sync/ingest \
  -H "Content-Type: application/json" \
  -d '{"baseEntityId":"123","firstName":"John","lastName":"Doe",...}'
```

### Sync FHIR Patient back to UCS
```bash
curl -X POST "http://localhost:8083/smart-bridge/api/sync/reverse?patientId=<fhir-patient-id>"
```

### Bulk Sync
```bash
curl -X POST "http://localhost:8083/smart-bridge/api/sync/bulk?batchSize=50"
```

### View Audit Logs
```bash
tail -f logs/audit.log
```

### Check Transformation Metrics
```bash
curl http://localhost:8083/smart-bridge/actuator/metrics/transformation.duration
```
