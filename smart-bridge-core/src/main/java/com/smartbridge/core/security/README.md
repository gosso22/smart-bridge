# Smart Bridge Security Implementation

## Overview

This package implements comprehensive security and encryption services for the Smart Bridge interoperability solution, fulfilling Requirements 8.1, 8.2, and 8.4.

## Components

### 1. TLS Configuration (`TLSConfig.java`)

**Purpose**: Configures TLS/SSL for all external communications

**Features**:
- TLS 1.3 protocol support (configurable)
- Custom keystore and truststore configuration
- Hostname verification
- Secure RestTemplate bean for HTTP clients

**Configuration Properties**:
```yaml
smartbridge.security.tls:
  enabled: true
  keystore-path: ${TLS_KEYSTORE_PATH}
  keystore-password: ${TLS_KEYSTORE_PASSWORD}
  truststore-path: ${TLS_TRUSTSTORE_PATH}
  truststore-password: ${TLS_TRUSTSTORE_PASSWORD}
  protocol: TLSv1.3
  verify-hostname: true
```

**Requirement**: 8.1 - Encrypt all communications using TLS

### 2. Encryption Service (`EncryptionService.java`)

**Purpose**: Provides encryption at rest for sensitive data storage

**Features**:
- AES-256-GCM authenticated encryption
- Random IV generation for each encryption operation
- Base64 encoding for storage compatibility
- Secure key management

**Usage**:
```java
@Autowired
private EncryptionService encryptionService;

// Encrypt sensitive data
String encrypted = encryptionService.encrypt("sensitive-data");

// Decrypt data
String decrypted = encryptionService.decrypt(encrypted);
```

**Configuration Properties**:
```yaml
smartbridge.security.encryption:
  key: ${ENCRYPTION_KEY}  # Base64-encoded AES-256 key
```

**Requirement**: 8.2 - Implement encryption at rest for sensitive data storage

### 3. Token Manager (`TokenManager.java`)

**Purpose**: Manages authentication tokens with automatic rotation capabilities

**Features**:
- Secure token generation using SecureRandom
- Token validation with grace period support
- Automatic token rotation
- Encrypted token storage
- Token metadata tracking

**Usage**:
```java
@Autowired
private TokenManager tokenManager;

// Generate new token
String token = tokenManager.generateToken("ucs-system");

// Validate token
boolean isValid = tokenManager.validateToken("ucs-system", token);

// Rotate token
String newToken = tokenManager.rotateToken("ucs-system");

// Store encrypted token
String encrypted = tokenManager.storeEncryptedToken("system-id", "token");
```

**Configuration Properties**:
```yaml
smartbridge.security.token:
  rotation-interval-hours: 24
  grace-period-hours: 1
  auto-rotation-enabled: true
```

**Requirement**: 8.4 - Support secure token management and rotation

### 4. Token Rotation Scheduler (`TokenRotationScheduler.java`)

**Purpose**: Automated scheduled token rotation

**Features**:
- Periodic token rotation checks (hourly by default)
- Configurable monitored systems
- Manual rotation trigger
- Token status monitoring

**Configuration Properties**:
```yaml
smartbridge.security.token:
  rotation-check-cron: "0 0 * * * *"  # Every hour
  monitored-systems: ucs-system,fhir-system,gothomis-system
```

### 5. Security Configuration (`SecurityConfig.java`)

**Purpose**: Main Spring Security configuration

**Features**:
- HTTPS enforcement
- Security headers (HSTS, CSP, X-Frame-Options)
- Stateless session management
- Endpoint authorization rules

### 6. Exception Classes

- `EncryptionException`: Thrown when encryption/decryption operations fail
- `SecurityConfigurationException`: Thrown when security configuration is invalid

## Security Best Practices

### TLS/SSL Configuration

1. **Production Environment**:
   - Always use valid SSL certificates from trusted CAs
   - Enable hostname verification
   - Use TLS 1.3 or TLS 1.2 minimum
   - Configure proper keystore and truststore

2. **Development Environment**:
   - Can use self-signed certificates for testing
   - Set `smartbridge.security.tls.enabled=false` only for local development

### Encryption Key Management

1. **Key Generation**:
   ```bash
   # Generate a secure AES-256 key
   openssl rand -base64 32
   ```

2. **Key Storage**:
   - Store encryption keys in secure key management systems (AWS KMS, HashiCorp Vault)
   - Never commit keys to version control
   - Use environment variables or secure configuration management

3. **Key Rotation**:
   - Implement periodic key rotation (recommended: annually)
   - Maintain old keys for decrypting existing data during transition

### Token Management

1. **Token Generation**:
   - Tokens are cryptographically secure (32 bytes from SecureRandom)
   - Each system has its own unique token

2. **Token Rotation**:
   - Automatic rotation every 24 hours (configurable)
   - Grace period allows old tokens to work for 1 hour after rotation
   - Manual rotation available for emergency scenarios

3. **Token Storage**:
   - Tokens can be stored encrypted using EncryptionService
   - In-memory storage for runtime (consider Redis for distributed systems)

## Testing

All security components have comprehensive unit tests:

- `TLSConfigTest`: Tests TLS configuration
- `EncryptionServiceTest`: Tests encryption/decryption operations
- `TokenManagerTest`: Tests token lifecycle management
- `TokenRotationSchedulerTest`: Tests automated rotation
- `SecurityConfigTest`: Tests security configuration

Run tests:
```bash
mvn test -pl smart-bridge-core -Dtest="com.smartbridge.core.security.*Test"
```

## Integration with Other Components

### FHIR Client Integration

```java
@Autowired
private RestTemplate secureRestTemplate;  // Configured with TLS

// Use for FHIR API calls
ResponseEntity<String> response = secureRestTemplate.getForEntity(
    fhirServerUrl + "/Patient/123", 
    String.class
);
```

### UCS API Integration

```java
@Autowired
private TokenManager tokenManager;

// Get token for UCS system
String token = tokenManager.getToken("ucs-system");

// Use in API calls
HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(token);
```

### Data Encryption

```java
@Autowired
private EncryptionService encryptionService;

// Encrypt patient data before storage
UCSClient client = ...;
String encryptedNationalId = encryptionService.encrypt(client.getNationalId());
client.setNationalId(encryptedNationalId);
```

## Monitoring and Auditing

All security operations are logged:
- Token generation and rotation events
- Encryption/decryption operations (without sensitive data)
- TLS connection establishment
- Authentication failures

Monitor these logs for security incidents and compliance auditing.

## Compliance

This implementation supports:
- HIPAA compliance for healthcare data protection
- GDPR requirements for data encryption
- Healthcare data protection standards
- Audit trail requirements

## Future Enhancements

Potential improvements for future releases:
1. Integration with external key management systems (AWS KMS, Azure Key Vault)
2. Multi-factor authentication support
3. Certificate rotation automation
4. Advanced threat detection
5. Rate limiting and DDoS protection
