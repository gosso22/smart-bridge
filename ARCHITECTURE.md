# Smart Bridge Local Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         YOUR LOCAL MACHINE                          │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │                    Docker Containers                          │ │
│  │                                                               │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │ │
│  │  │  HAPI FHIR  │  │  OpenHIM    │  │  RabbitMQ   │         │ │
│  │  │   :8082     │  │   :8080     │  │   :5672     │         │ │
│  │  │             │  │   :9000     │  │   :15672    │         │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘         │ │
│  │         ▲               ▲                  ▲                 │ │
│  └─────────┼───────────────┼──────────────────┼─────────────────┘ │
│            │               │                  │                   │
│            │               │                  │                   │
│  ┌─────────┴───────────────┴──────────────────┴─────────────────┐ │
│  │                                                               │ │
│  │              Smart Bridge Application (:8083)                │ │
│  │                                                               │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │ │
│  │  │Transform │  │ Mediator │  │   Flow   │  │ Security │   │ │
│  │  │  Engine  │  │ Services │  │ Services │  │  & Audit │   │ │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │ │
│  │                                                               │ │
│  └───────────────────────────────┬───────────────────────────────┘ │
│                                  │                                 │
└──────────────────────────────────┼─────────────────────────────────┘
                                   │
                                   │ HTTPS
                                   ▼
                        ┌─────────────────────┐
                        │   UCS System        │
                        │   (Your Cloud)      │
                        │                     │
                        │  your-ucs-url.com   │
                        └─────────────────────┘
```

## Data Flow

### 1. UCS → FHIR (Ingestion)

```
┌─────────┐      ┌──────────────┐      ┌──────────┐      ┌──────────┐
│   UCS   │─────▶│ Smart Bridge │─────▶│Transform │─────▶│   FHIR   │
│ (Cloud) │      │   Mediator   │      │  Engine  │      │ (Local)  │
└─────────┘      └──────────────┘      └──────────┘      └──────────┘
                        │
                        ▼
                 ┌──────────────┐
                 │   OpenHIM    │
                 │ (Audit Log)  │
                 └──────────────┘
```

### 2. FHIR → UCS (Reverse Sync)

```
┌──────────┐      ┌──────────────┐      ┌──────────┐      ┌─────────┐
│   FHIR   │─────▶│ Smart Bridge │─────▶│Transform │─────▶│   UCS   │
│ (Local)  │      │   Webhook    │      │  Engine  │      │ (Cloud) │
└──────────┘      └──────────────┘      └──────────┘      └─────────┘
                        │
                        ▼
                 ┌──────────────┐
                 │   RabbitMQ   │
                 │ (Queue/Retry)│
                 └──────────────┘
```

## Port Mapping

| Port  | Service              | Purpose                    |
|-------|----------------------|----------------------------|
| 8083  | Smart Bridge         | Main application API       |
| 8082  | HAPI FHIR            | FHIR R4 server             |
| 8080  | OpenHIM Core         | Mediator routing           |
| 9000  | OpenHIM Console      | Web UI for OpenHIM         |
| 5672  | RabbitMQ             | Message queue (AMQP)       |
| 15672 | RabbitMQ Management  | Web UI for RabbitMQ        |
| 27017 | MongoDB              | OpenHIM database           |

## API Endpoints

### Smart Bridge

```
http://localhost:8083/smart-bridge/
├── api/
│   └── sync/
│       ├── ingest          [POST] - Sync UCS → FHIR
│       ├── reverse         [POST] - Sync FHIR → UCS
│       └── bulk            [POST] - Bulk sync
├── actuator/
│   ├── health             [GET]  - Health check
│   ├── metrics            [GET]  - Prometheus metrics
│   └── prometheus         [GET]  - Metrics endpoint
└── webhook/
    └── fhir-change        [POST] - FHIR change notification
```

### HAPI FHIR

```
http://localhost:8082/fhir/
├── metadata               [GET]  - Capability statement
├── Patient                [GET]  - Search patients
├── Patient/{id}           [GET]  - Get patient
├── Patient                [POST] - Create patient
└── Patient/{id}           [PUT]  - Update patient
```

## Configuration Files

```
smart-bridge/
├── .env                          # Your UCS credentials (EDIT THIS!)
├── docker-compose.yml            # Infrastructure setup
├── deploy-local.sh               # Automated deployment
├── test-local-deployment.sh      # Testing script
├── test-data/
│   └── test-ucs-client.json     # Sample test data
└── smart-bridge-application/
    └── src/main/resources/
        ├── application.yml       # Main config
        ├── application-dev.yml   # Dev profile
        └── .env.template         # Template for .env
```

## Monitoring & Logs

```
Logs Location:
├── logs/
│   ├── smart-bridge.log         # Application logs
│   ├── audit.log                # Audit trail
│   └── security.log             # Security events

Monitoring URLs:
├── http://localhost:8083/smart-bridge/actuator/health
├── http://localhost:8083/smart-bridge/actuator/metrics
├── http://localhost:15672       # RabbitMQ queues
└── http://localhost:9000        # OpenHIM transactions
```

## Security Notes

### Default Credentials (CHANGE IN PRODUCTION!)

```
OpenHIM Console:
  Email:    root@openhim.org
  Password: openhim-password

RabbitMQ:
  Username: smartbridge
  Password: smartbridge123

Smart Bridge Mediator (OpenHIM Client):
  Client ID: smart-bridge-mediator
  Password:  smartbridge123
```

### Environment Variables (.env)

```properties
# Your UCS System (REQUIRED - EDIT THIS!)
UCS_API_URL=https://your-ucs-system.com/api
UCS_USERNAME=your-username
UCS_PASSWORD=your-password

# Security Keys (CHANGE THESE!)
ENCRYPTION_KEY=changeme-32-character-key-here
TOKEN_SECRET=changeme-secret-key-here
```

## Deployment Checklist

- [ ] Docker and Docker Compose installed
- [ ] Java 17 installed
- [ ] Maven installed
- [ ] Edit `.env` with your UCS credentials
- [ ] Run `./deploy-local.sh`
- [ ] Wait for services to start (2-3 minutes)
- [ ] Configure OpenHIM (create client and channels)
- [ ] Start Smart Bridge application
- [ ] Run `./test-local-deployment.sh`
- [ ] Test with your actual UCS data

## Quick Commands

```bash
# Deploy everything
./deploy-local.sh

# Start Smart Bridge
mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev

# Test deployment
./test-local-deployment.sh

# Check health
curl http://localhost:8083/smart-bridge/actuator/health

# View logs
tail -f logs/smart-bridge.log

# Stop everything
docker-compose down
```

## Troubleshooting

### Check Service Status
```bash
docker-compose ps
```

### View Service Logs
```bash
docker-compose logs hapi-fhir
docker-compose logs openhim-core
docker-compose logs rabbitmq
```

### Test UCS Connection
```bash
curl -u username:password https://your-ucs-system.com/api/health
```

### Check Port Availability
```bash
lsof -i :8083  # Smart Bridge
lsof -i :8082  # HAPI FHIR
lsof -i :8080  # OpenHIM
```

### Restart Services
```bash
docker-compose restart <service-name>
```

## Next Steps

1. ✅ Understand the architecture (you're here!)
2. ⬜ Follow `QUICKSTART.md` to deploy
3. ⬜ Read `LOCAL_DEPLOYMENT.md` for details
4. ⬜ Test with your UCS data
5. ⬜ Monitor and optimize
