# Smart Bridge Interoperability Solution

A hub-and-spoke interoperability solution connecting legacy health systems (UCS and GoTHOMIS) with modern FHIR-based ecosystems using OpenHIM as the central routing and transformation engine.

## Project Structure

This is a Maven multi-module project with the following structure:

```
smart-bridge-parent/
├── smart-bridge-core/           # Core data models and interfaces
├── smart-bridge-transformation/ # Data transformation services
├── smart-bridge-mediators/      # OpenHIM mediator services
└── smart-bridge-application/    # Main Spring Boot application
```

### Modules

#### smart-bridge-core
Core data models and interfaces for the Smart Bridge system:
- **UCS Data Models**: `UCSClient` with nested classes for identifiers, demographics, clinical data, and metadata
- **FHIR Resource Wrapper**: `FHIRResourceWrapper` for managing FHIR R4 resources
- **Service Interfaces**: `TransformationService` and `MediatorService` for system integration
- **Audit Logging**: `AuditLogger` for comprehensive audit trails
- **Monitoring Configuration**: Micrometer-based metrics collection

#### smart-bridge-transformation
Data transformation services for converting between UCS and FHIR formats (to be implemented in future tasks).

#### smart-bridge-mediators
OpenHIM mediator services for system-specific interactions (to be implemented in future tasks).

#### smart-bridge-application
Main Spring Boot application that wires all components together.

## Technology Stack

- **Java**: 17
- **Spring Boot**: 3.2.0
- **HAPI FHIR**: 6.8.0 (R4)
- **Logging**: SLF4J with Logback
- **Monitoring**: Micrometer with Prometheus
- **Testing**: JUnit 5 with jqwik for property-based testing
- **Build Tool**: Maven

## Building the Project

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application
mvn package

# Run application
mvn spring-boot:run -pl smart-bridge-application
```

## Configuration

Application configuration is located in `smart-bridge-application/src/main/resources/application.yml`.

### Profiles

- **dev**: Development profile with debug logging
- **prod**: Production profile with optimized logging and strict validation

### Key Configuration Properties

```yaml
smart-bridge:
  openhim:
    core-url: http://localhost:8080
  fhir:
    server-url: http://localhost:8082/fhir
  ucs:
    api-url: http://localhost:8081/ucs
  monitoring:
    metrics-enabled: true
    audit-enabled: true
```

## Logging

The system uses Logback with multiple appenders:

- **Console**: Development logging
- **File**: Application logs (30-day retention)
- **Audit**: Compliance audit logs (365-day retention)
- **Security**: Security event logs (365-day retention)

Log files are stored in the `logs/` directory.

## Monitoring

Metrics are exposed via Spring Boot Actuator endpoints:

- Health: `/smart-bridge/actuator/health`
- Metrics: `/smart-bridge/actuator/metrics`
- Prometheus: `/smart-bridge/actuator/prometheus`

Custom metrics include:
- Transformation duration and success/error counts
- Mediator operation timing
- FHIR and UCS operation counts
- Audit log and security event counts

## Next Steps

Refer to `.kiro/specs/smart-bridge/tasks.md` for the implementation plan. The next tasks involve:

1. Implementing data models with JSON schema validation
2. Building the core transformation engine
3. Integrating HAPI FHIR client services
4. Developing OpenHIM mediators
5. Implementing error handling and message queuing

## License

Copyright © 2026 Smart Bridge Project
