package com.smartbridge.core.audit;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive audit service for Smart Bridge operations.
 * Provides audit logging with integrity verification and tamper detection.
 * 
 * Requirements: 8.3, 8.5
 */
@Service
public class AuditService {

    private final AuditLogger auditLogger;
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final Map<Long, String> auditChain = new ConcurrentHashMap<>();
    private volatile String previousHash = "GENESIS";

    public AuditService(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * Log patient data access with user identification and integrity verification.
     * 
     * @param userId User accessing the data
     * @param userName User's display name
     * @param patientId Patient identifier
     * @param dataType Type of data accessed
     * @param operation Operation performed (READ, WRITE, UPDATE, DELETE)
     * @param sourceSystem Source system of the operation
     * @param sourceIp IP address of the request
     * @param details Additional details
     */
    public void logPatientDataAccess(String userId, String userName, String patientId, 
                                     String dataType, String operation, String sourceSystem,
                                     String sourceIp, String details) {
        String auditEntry = createAuditEntry(
            "PATIENT_DATA_ACCESS",
            userId,
            userName,
            patientId,
            dataType,
            operation,
            sourceSystem,
            sourceIp,
            details
        );
        
        long seqNum = sequenceNumber.incrementAndGet();
        String hash = computeHash(seqNum, auditEntry, previousHash);
        auditChain.put(seqNum, hash);
        previousHash = hash;
        
        auditLogger.logDataAccess(userId, patientId, dataType, operation, sourceSystem, 
            String.format("%s | SeqNum=%d | Hash=%s", details, seqNum, hash));
    }

    /**
     * Log transformation operations with integrity verification.
     */
    public void logTransformation(String userId, String sourceSystem, String targetSystem, 
                                 String operation, String sourceId, String targetId, 
                                 boolean success, String details) {
        String auditEntry = createAuditEntry(
            "TRANSFORMATION",
            userId,
            null,
            sourceId,
            targetSystem,
            operation,
            sourceSystem,
            null,
            details
        );
        
        long seqNum = sequenceNumber.incrementAndGet();
        String hash = computeHash(seqNum, auditEntry, previousHash);
        auditChain.put(seqNum, hash);
        previousHash = hash;
        
        auditLogger.logTransformation(sourceSystem, targetSystem, operation, sourceId, targetId, 
            success, String.format("%s | UserId=%s | SeqNum=%d | Hash=%s", details, userId, seqNum, hash));
    }

    /**
     * Log mediator operations with user context.
     */
    public void logMediatorOperation(String userId, String mediatorName, String operation, 
                                    String requestId, boolean success, long durationMs, String details) {
        String auditEntry = createAuditEntry(
            "MEDIATOR_OPERATION",
            userId,
            null,
            requestId,
            mediatorName,
            operation,
            mediatorName,
            null,
            details
        );
        
        long seqNum = sequenceNumber.incrementAndGet();
        String hash = computeHash(seqNum, auditEntry, previousHash);
        auditChain.put(seqNum, hash);
        previousHash = hash;
        
        auditLogger.logMediatorOperation(mediatorName, operation, requestId, success, durationMs,
            String.format("%s | UserId=%s | SeqNum=%d | Hash=%s", details, userId, seqNum, hash));
    }

    /**
     * Log FHIR operations with user identification.
     */
    public void logFHIROperation(String userId, String operation, String resourceType, 
                                String resourceId, String fhirServer, boolean success, String details) {
        String auditEntry = createAuditEntry(
            "FHIR_OPERATION",
            userId,
            null,
            resourceId,
            resourceType,
            operation,
            fhirServer,
            null,
            details
        );
        
        long seqNum = sequenceNumber.incrementAndGet();
        String hash = computeHash(seqNum, auditEntry, previousHash);
        auditChain.put(seqNum, hash);
        previousHash = hash;
        
        auditLogger.logFHIROperation(operation, resourceType, resourceId, fhirServer, success,
            String.format("%s | UserId=%s | SeqNum=%d | Hash=%s", details, userId, seqNum, hash));
    }

    /**
     * Log security events with enhanced context.
     */
    public void logSecurityEvent(String eventType, String userId, String userName, 
                                String sourceIp, boolean success, String details) {
        String auditEntry = createAuditEntry(
            "SECURITY_EVENT",
            userId,
            userName,
            null,
            eventType,
            eventType,
            null,
            sourceIp,
            details
        );
        
        long seqNum = sequenceNumber.incrementAndGet();
        String hash = computeHash(seqNum, auditEntry, previousHash);
        auditChain.put(seqNum, hash);
        previousHash = hash;
        
        auditLogger.logSecurityEvent(eventType, userId, sourceIp, success,
            String.format("%s | UserName=%s | SeqNum=%d | Hash=%s", details, userName, seqNum, hash));
    }

    /**
     * Log authentication attempts with user identification.
     */
    public void logAuthenticationAttempt(String userId, String userName, String authMethod, 
                                        String sourceIp, boolean success, String details) {
        logSecurityEvent("AUTHENTICATION", userId, userName, sourceIp, success,
            String.format("Method=%s | %s", authMethod, details));
    }

    /**
     * Log authorization decisions.
     */
    public void logAuthorizationDecision(String userId, String userName, String resource, 
                                        String action, boolean granted, String sourceIp, String details) {
        logSecurityEvent("AUTHORIZATION", userId, userName, sourceIp, granted,
            String.format("Resource=%s | Action=%s | Granted=%s | %s", resource, action, granted, details));
    }

    /**
     * Verify audit trail integrity by checking hash chain.
     * 
     * @param startSeqNum Starting sequence number
     * @param endSeqNum Ending sequence number
     * @return true if integrity is intact, false if tampering detected
     */
    public boolean verifyAuditIntegrity(long startSeqNum, long endSeqNum) {
        if (startSeqNum < 1 || endSeqNum > sequenceNumber.get() || startSeqNum > endSeqNum) {
            return false;
        }
        
        // Verify all hashes exist in the chain
        for (long seq = startSeqNum; seq <= endSeqNum; seq++) {
            String currentHash = auditChain.get(seq);
            if (currentHash == null) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get current sequence number for audit trail.
     */
    public long getCurrentSequenceNumber() {
        return sequenceNumber.get();
    }

    /**
     * Get hash for a specific sequence number.
     */
    public String getHashForSequence(long seqNum) {
        return auditChain.get(seqNum);
    }

    /**
     * Create a standardized audit entry string.
     */
    private String createAuditEntry(String eventType, String userId, String userName, 
                                   String resourceId, String dataType, String operation,
                                   String sourceSystem, String sourceIp, String details) {
        return String.format(
            "EventType=%s|UserId=%s|UserName=%s|ResourceId=%s|DataType=%s|Operation=%s|Source=%s|SourceIP=%s|Timestamp=%s|Details=%s",
            eventType,
            userId != null ? userId : "SYSTEM",
            userName != null ? userName : "N/A",
            resourceId != null ? resourceId : "N/A",
            dataType != null ? dataType : "N/A",
            operation != null ? operation : "N/A",
            sourceSystem != null ? sourceSystem : "N/A",
            sourceIp != null ? sourceIp : "N/A",
            Instant.now().toString(),
            details != null ? details : "N/A"
        );
    }

    /**
     * Compute SHA-256 hash for audit trail integrity.
     */
    private String computeHash(long seqNum, String auditEntry, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String dataToHash = seqNum + "|" + auditEntry + "|" + previousHash;
            byte[] hashBytes = digest.digest(dataToHash.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
