# Smart Bridge Application - Deployment Guide

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- RabbitMQ 3.12+
- Access to FHIR server and UCS system

### Environment Setup

1. Copy the environment template:
```bash
cp src/main/resources/.env.template .env
```

2. Edit `.env` and configure your environment variables:
   - OpenHIM connection details
   - FHIR server URL
   - UCS system URL
   - RabbitMQ connection
   - Security credentials

3. Source the environment file:
```bash
export $(cat .env | xargs)
```

### Build and Run

#### Development Mode

```bash
# Build the project
mvn clean package -DskipTests

# Run with dev profile
mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev
```

#### Staging Mode

```bash
export SPRING_PROFILES_ACTIVE=staging
mvn spring-boot:run -pl smart-bridge-application
```

#### Production Mode

```bash
# Build production JAR
mvn clean package -Pprod

# Run production JAR
java -jar smart-bridge-application/target/smart-bridge-application-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

## Configuration Profiles

### Development (`dev`)
- Debug logging enabled
- Relaxed validation
- Local service URLs
- Self-signed certificates allowed

### Staging (`staging`)
- Info-level logging
- Strict validation enabled
- Internal staging URLs
- TLS verification enabled

### Production (`prod`)
- Warn-level logging
- Maximum security settings
- Production URLs
- Enhanced thread pools
- Aggressive circuit breakers

## Environment Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `OPENHIM_CORE_URL` | OpenHIM core API URL | `https://openhim.example.com:8080` |
| `OPENHIM_USERNAME` | OpenHIM API username | `root@openhim.org` |
| `OPENHIM_PASSWORD` | OpenHIM API password | `secure-password` |
| `FHIR_SERVER_URL` | HAPI FHIR server URL | `https://fhir.example.com/fhir` |
| `UCS_API_URL` | UCS system API URL | `https://ucs.example.com/api` |
| `RABBITMQ_HOST` | RabbitMQ hostname | `rabbitmq.example.com` |
| `RABBITMQ_USERNAME` | RabbitMQ username | `smartbridge` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `secure-password` |
| `ENCRYPTION_KEY` | Data encryption key (32+ chars) | `your-strong-encryption-key-here` |

### Optional Variables

See `.env.template` for complete list of optional configuration variables.

## Health Checks

Once running, check application health:

```bash
# Overall health
curl http://localhost:8080/smart-bridge/actuator/health

# Detailed health (requires authorization)
curl http://localhost:8080/smart-bridge/actuator/health \
  -H "Authorization: Bearer <token>"
```

## Monitoring Endpoints

- **Health**: `/smart-bridge/actuator/health`
- **Metrics**: `/smart-bridge/actuator/metrics`
- **Prometheus**: `/smart-bridge/actuator/prometheus`
- **Info**: `/smart-bridge/actuator/info`

## Graceful Shutdown

The application supports graceful shutdown:

```bash
# Send SIGTERM
kill -15 <pid>

# Or use Spring Boot Actuator
curl -X POST http://localhost:8080/smart-bridge/actuator/shutdown
```

Shutdown process:
1. Stop accepting new work (change detection, message consumers)
2. Wait for in-flight tasks to complete (30s timeout)
3. Close external connections
4. Exit

## Troubleshooting

### Application won't start

1. Check Java version: `java -version` (must be 17+)
2. Verify environment variables are set
3. Check RabbitMQ is running: `rabbitmqctl status`
4. Review logs in `logs/application.log`

### Connection failures

1. Verify network connectivity to external systems
2. Check firewall rules
3. Validate TLS certificates if using HTTPS
4. Review security logs in `logs/security.log`

### Performance issues

1. Check metrics: `/actuator/metrics`
2. Review thread pool settings in configuration
3. Monitor circuit breaker status
4. Check RabbitMQ queue depths

## Logging

Log files are located in the `logs/` directory:

- `application.log` - General application logs (30-day retention)
- `audit.log` - Compliance audit logs (365-day retention)
- `security.log` - Security events (365-day retention)

## Security Considerations

### Production Checklist

- [ ] Change default passwords
- [ ] Use strong encryption key (32+ characters)
- [ ] Enable TLS for all connections
- [ ] Configure proper keystores and truststores
- [ ] Enable token rotation
- [ ] Restrict actuator endpoints
- [ ] Configure firewall rules
- [ ] Enable audit logging
- [ ] Set up log monitoring and alerting

### TLS Configuration

1. Generate keystore:
```bash
keytool -genkeypair -alias smartbridge \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 \
  -validity 3650
```

2. Set environment variables:
```bash
export TLS_ENABLED=true
export TLS_KEYSTORE_PATH=/path/to/keystore.p12
export TLS_KEYSTORE_PASSWORD=your-password
```

## Docker Deployment

### Build Docker Image

```bash
# Build application
mvn clean package -DskipTests

# Build Docker image
docker build -t smart-bridge:latest -f smart-bridge-application/Dockerfile .
```

### Run with Docker Compose

```bash
docker-compose up -d
```

See `docker-compose.yml` for complete configuration.

## Kubernetes Deployment

See `k8s/` directory for Kubernetes manifests:

- `deployment.yaml` - Application deployment
- `service.yaml` - Service definition
- `configmap.yaml` - Configuration
- `secret.yaml` - Sensitive data

Deploy:
```bash
kubectl apply -f k8s/
```

## Support

For issues or questions:
- Check logs in `logs/` directory
- Review configuration in `application.yml`
- Consult API documentation
- Contact system administrator
