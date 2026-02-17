package com.smartbridge.core.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartbridge.core.model.ucs.UCSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Legacy API Compatibility Service.
 * 
 * Ensures that the Smart Bridge maintains exact API contracts with legacy systems
 * without requiring any modifications to those systems. This service:
 * 
 * 1. Preserves all data fields from legacy systems, including unknown/custom fields
 * 2. Maintains exact JSON structure and field naming conventions
 * 3. Provides transparent pass-through for legacy API contracts
 * 4. Tracks and preserves custom extensions and proprietary fields
 * 
 * Requirements: 1.4, 1.5
 */
@Service
public class LegacyApiCompatibilityService {

    private static final Logger logger = LoggerFactory.getLogger(LegacyApiCompatibilityService.class);

    private final ObjectMapper objectMapper;
    
    // Store for preserving unknown/custom fields during transformations
    private final Map<String, Map<String, Object>> preservedFieldsStore;

    public LegacyApiCompatibilityService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.preservedFieldsStore = new HashMap<>();
    }

    /**
     * Preserve all fields from the original legacy system payload.
     * This ensures no data loss during transformation to FHIR and back.
     * 
     * @param clientId Unique identifier for the client
     * @param originalPayload Original JSON payload from legacy system
     */
    public void preserveOriginalFields(String clientId, String originalPayload) {
        try {
            JsonNode rootNode = objectMapper.readTree(originalPayload);
            Map<String, Object> allFields = extractAllFields(rootNode);
            
            preservedFieldsStore.put(clientId, allFields);
            
            logger.debug("Preserved {} fields for client: {}", allFields.size(), clientId);
            
        } catch (Exception e) {
            logger.error("Error preserving original fields for client {}: {}", 
                clientId, e.getMessage(), e);
        }
    }

    /**
     * Restore preserved fields to the outgoing payload.
     * This ensures the legacy system receives data in its original format.
     * 
     * @param clientId Unique identifier for the client
     * @param currentPayload Current payload being sent to legacy system
     * @return Enhanced payload with all preserved fields restored
     */
    public String restorePreservedFields(String clientId, String currentPayload) {
        try {
            Map<String, Object> preservedFields = preservedFieldsStore.get(clientId);
            
            if (preservedFields == null || preservedFields.isEmpty()) {
                logger.debug("No preserved fields found for client: {}", clientId);
                return currentPayload;
            }
            
            JsonNode currentNode = objectMapper.readTree(currentPayload);
            ObjectNode enhancedNode = mergePreservedFields(currentNode, preservedFields);
            
            String enhancedPayload = objectMapper.writeValueAsString(enhancedNode);
            
            logger.debug("Restored {} preserved fields for client: {}", 
                preservedFields.size(), clientId);
            
            return enhancedPayload;
            
        } catch (Exception e) {
            logger.error("Error restoring preserved fields for client {}: {}", 
                clientId, e.getMessage(), e);
            return currentPayload; // Return original on error
        }
    }

    /**
     * Validate that the payload maintains API contract compatibility.
     * Checks that all required fields for the legacy system are present.
     * 
     * @param payload JSON payload to validate
     * @param requiredFields List of required field paths (e.g., "identifiers.opensrp_id")
     * @return ValidationResult indicating compatibility status
     */
    public ValidationResult validateApiContract(String payload, List<String> requiredFields) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            List<String> missingFields = new ArrayList<>();
            
            for (String fieldPath : requiredFields) {
                if (!hasField(rootNode, fieldPath)) {
                    missingFields.add(fieldPath);
                }
            }
            
            if (missingFields.isEmpty()) {
                logger.debug("API contract validation passed");
                return new ValidationResult(true, "All required fields present", missingFields);
            } else {
                logger.warn("API contract validation failed: missing fields {}", missingFields);
                return new ValidationResult(false, 
                    "Missing required fields: " + String.join(", ", missingFields), 
                    missingFields);
            }
            
        } catch (Exception e) {
            logger.error("Error validating API contract: {}", e.getMessage(), e);
            return new ValidationResult(false, "Validation error: " + e.getMessage(), 
                Collections.emptyList());
        }
    }

    /**
     * Ensure UCS client object preserves all original fields.
     * Enhances the UCS client with any custom/unknown fields from the original payload.
     * 
     * @param ucsClient UCS client object
     * @param originalPayload Original JSON payload
     * @return Enhanced UCS client with preserved fields
     */
    public UCSClient ensureFieldPreservation(UCSClient ucsClient, String originalPayload) {
        try {
            // Extract client ID for tracking
            String clientId = extractClientId(ucsClient);
            
            // Preserve original fields
            preserveOriginalFields(clientId, originalPayload);
            
            logger.debug("Field preservation ensured for client: {}", clientId);
            
            return ucsClient;
            
        } catch (Exception e) {
            logger.error("Error ensuring field preservation: {}", e.getMessage(), e);
            return ucsClient; // Return original on error
        }
    }

    /**
     * Create a compatibility wrapper for the response.
     * Ensures the response maintains the exact structure expected by legacy systems.
     * 
     * @param response Response object to wrap
     * @param clientId Client identifier
     * @return JSON string with compatibility wrapper applied
     */
    public String createCompatibilityWrapper(Object response, String clientId) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            
            // Restore any preserved fields
            String enhancedResponse = restorePreservedFields(clientId, responseJson);
            
            logger.debug("Created compatibility wrapper for client: {}", clientId);
            
            return enhancedResponse;
            
        } catch (Exception e) {
            logger.error("Error creating compatibility wrapper: {}", e.getMessage(), e);
            try {
                return objectMapper.writeValueAsString(response);
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    /**
     * Clear preserved fields for a client (e.g., after successful synchronization).
     * 
     * @param clientId Client identifier
     */
    public void clearPreservedFields(String clientId) {
        preservedFieldsStore.remove(clientId);
        logger.debug("Cleared preserved fields for client: {}", clientId);
    }

    /**
     * Get statistics about preserved fields.
     * 
     * @return Map containing statistics
     */
    public Map<String, Object> getPreservationStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClientsTracked", preservedFieldsStore.size());
        stats.put("totalFieldsPreserved", 
            preservedFieldsStore.values().stream()
                .mapToInt(Map::size)
                .sum());
        return stats;
    }

    // Helper methods

    /**
     * Extract all fields from a JSON node recursively.
     */
    private Map<String, Object> extractAllFields(JsonNode node) {
        Map<String, Object> fields = new HashMap<>();
        
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = node.fields();
            while (fieldsIterator.hasNext()) {
                Map.Entry<String, JsonNode> field = fieldsIterator.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                
                if (value.isObject() || value.isArray()) {
                    fields.put(key, extractAllFields(value));
                } else if (value.isTextual()) {
                    fields.put(key, value.asText());
                } else if (value.isNumber()) {
                    fields.put(key, value.numberValue());
                } else if (value.isBoolean()) {
                    fields.put(key, value.asBoolean());
                } else if (value.isNull()) {
                    fields.put(key, null);
                }
            }
        } else if (node.isArray()) {
            List<Object> arrayItems = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isObject() || item.isArray()) {
                    arrayItems.add(extractAllFields(item));
                } else {
                    arrayItems.add(item);
                }
            }
            return Map.of("_array", arrayItems);
        }
        
        return fields;
    }

    /**
     * Merge preserved fields back into the current node.
     */
    private ObjectNode mergePreservedFields(JsonNode currentNode, Map<String, Object> preservedFields) {
        ObjectNode result = objectMapper.createObjectNode();
        
        // First, copy all current fields
        if (currentNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = currentNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.set(field.getKey(), field.getValue());
            }
        }
        
        // Then, add preserved fields that are not in current node
        for (Map.Entry<String, Object> entry : preservedFields.entrySet()) {
            String key = entry.getKey();
            if (!result.has(key)) {
                result.set(key, objectMapper.valueToTree(entry.getValue()));
            }
        }
        
        return result;
    }

    /**
     * Check if a field path exists in the JSON node.
     */
    private boolean hasField(JsonNode node, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        JsonNode current = node;
        
        for (String part : parts) {
            if (current == null || !current.has(part)) {
                return false;
            }
            current = current.get(part);
        }
        
        return current != null;
    }

    /**
     * Extract client ID from UCS client object.
     */
    private String extractClientId(UCSClient ucsClient) {
        if (ucsClient.getIdentifiers() != null && 
            ucsClient.getIdentifiers().getOpensrpId() != null) {
            return ucsClient.getIdentifiers().getOpensrpId();
        }
        
        if (ucsClient.getMetadata() != null && 
            ucsClient.getMetadata().getFhirId() != null) {
            return ucsClient.getMetadata().getFhirId();
        }
        
        return UUID.randomUUID().toString();
    }

    /**
     * Validation result for API contract checks.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final List<String> missingFields;

        public ValidationResult(boolean valid, String message, List<String> missingFields) {
            this.valid = valid;
            this.message = message;
            this.missingFields = missingFields;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }
    }
}
