# Smart Bridge - Quick Start Guide

## Application Status
✅ **Smart Bridge is running successfully!**

## Configuration

### Endpoints
- **UCS API**: https://ucs-opensrp-dev.d-tree.org/opensrp
- **FHIR Server**: http://localhost:8082/fhir
- **Smart Bridge**: http://localhost:8080/smart-bridge

### Application URLs
- **Health Check**: http://localhost:8080/smart-bridge/actuator/health
- **Metrics**: http://localhost:8080/smart-bridge/actuator/metrics
- **Prometheus**: http://localhost:8080/smart-bridge/actuator/prometheus

### API Endpoints
- **FHIR Webhook**: http://localhost:8080/smart-bridge/fhir/webhook/notification
- **FHIR Resource Webhook**: http://localhost:8080/smart-bridge/fhir/webhook/resource/{resourceType}
- **Mediator Health**: http://localhost:8080/smart-bridge/mediator/health
- **API Health**: http://localhost:8080/smart-bridge/api/health

## Services Running
- ✅ Tomcat Web Server (port 8080)
- ✅ RabbitMQ Message Queue (localhost:5672)
- ✅ FHIR Client Service
- ✅ Transformation Services
- ✅ Circuit Breakers & Retry Policies
- ✅ Security & Encryption Services
- ✅ Monitoring & Metrics

## Testing the Application

### 1. Check Health Status
```bash
curl http://localhost:8080/smart-bridge/actuator/health
```

### 2. Check FHIR Server Connection
```bash
curl http://localhost:8082/fhir/metadata
```

### 3. Test FHIR Webhook
```bash
curl -X POST http://localhost:8080/smart-bridge/fhir/webhook/notification \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Bundle",
    "type": "transaction",
    "entry": []
  }'
```

## Stopping the Application
```bash
# Find the process
ps aux | grep smart-bridge-application

# Kill the process
pkill -f "smart-bridge-application"
```

## Restarting the Application
```bash
cd /work/integration/smart-bridge

# Set environment variables
export ENCRYPTION_KEY="v+2I5EaJfTT4BmMdQz7AvZGyDZ03RoG6l/eBVssBREk="
export UCS_API_URL="https://ucs-opensrp-dev.d-tree.org/opensrp"
export FHIR_SERVER_URL="http://localhost:8082/fhir"

# Start the application
mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev
```

## Logs
Application logs are being written to: `/work/integration/smart-bridge/smart-bridge.log`

View logs in real-time:
```bash
tail -f /work/integration/smart-bridge/smart-bridge.log
```

## Next Steps

1. **Configure FHIR Webhooks**: Set up subscriptions on your FHIR server to send notifications to Smart Bridge
2. **Test UCS Integration**: Verify connectivity to your UCS system
3. **Monitor Metrics**: Check Prometheus metrics for system health
4. **Review Audit Logs**: Check the `logs/` directory for audit trails

## Troubleshooting

### Port Already in Use
```bash
lsof -ti:8080 | xargs kill -9
```

### RabbitMQ Not Running
```bash
docker start rabbitmq-smartbridge
```

### Check Application Status
```bash
curl http://localhost:8080/smart-bridge/actuator/health
```

## Environment Variables
All configuration is stored in `.env` file:
- `ENCRYPTION_KEY`: Base64-encoded encryption key
- `UCS_API_URL`: UCS system endpoint
- `FHIR_SERVER_URL`: FHIR server endpoint
- `RABBITMQ_HOST`: RabbitMQ host (default: localhost)

## Security Notes
- Default security password is generated on startup (check logs)
- For production, update security configuration
- Use strong encryption keys
- Enable TLS for production deployments
