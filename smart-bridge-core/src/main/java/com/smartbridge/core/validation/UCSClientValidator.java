package com.smartbridge.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.smartbridge.core.model.ucs.UCSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for UCS Client data using JSON Schema validation.
 * Validates UCS Client objects against the defined JSON schema to ensure data integrity.
 */
@Component
public class UCSClientValidator {

    private static final Logger logger = LoggerFactory.getLogger(UCSClientValidator.class);
    private static final String SCHEMA_PATH = "/schemas/ucs-client-schema.json";

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public UCSClientValidator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Register JSR310 module for date/time
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.schema = loadSchema();
    }

    /**
     * Load the UCS Client JSON schema from resources.
     */
    private JsonSchema loadSchema() {
        try {
            InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH);
            if (schemaStream == null) {
                throw new IllegalStateException("UCS Client schema not found at: " + SCHEMA_PATH);
            }
            
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            return factory.getSchema(schemaStream);
        } catch (Exception e) {
            logger.error("Failed to load UCS Client schema", e);
            throw new IllegalStateException("Failed to initialize UCS Client validator", e);
        }
    }

    /**
     * Validate a UCS Client object against the JSON schema.
     * 
     * @param client The UCS Client object to validate
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validate(UCSClient client) {
        if (client == null) {
            return ValidationResult.invalid("UCS Client object cannot be null");
        }

        try {
            // Convert UCSClient to JsonNode for schema validation
            JsonNode jsonNode = objectMapper.valueToTree(client);
            
            // Perform schema validation
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            
            if (errors.isEmpty()) {
                logger.debug("UCS Client validation successful");
                return ValidationResult.valid();
            } else {
                String errorMessage = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
                
                logger.warn("UCS Client validation failed: {}", errorMessage);
                return ValidationResult.invalid(errorMessage);
            }
        } catch (Exception e) {
            logger.error("Error during UCS Client validation", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate a JSON string against the UCS Client schema.
     * 
     * @param jsonString The JSON string to validate
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validateJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return ValidationResult.invalid("JSON string cannot be null or empty");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            
            if (errors.isEmpty()) {
                logger.debug("UCS Client JSON validation successful");
                return ValidationResult.valid();
            } else {
                String errorMessage = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
                
                logger.warn("UCS Client JSON validation failed: {}", errorMessage);
                return ValidationResult.invalid(errorMessage);
            }
        } catch (Exception e) {
            logger.error("Error parsing or validating JSON", e);
            return ValidationResult.invalid("JSON parsing error: " + e.getMessage());
        }
    }

    /**
     * Parse and validate a JSON string, returning a UCSClient object if valid.
     * 
     * @param jsonString The JSON string to parse and validate
     * @return The parsed UCSClient object
     * @throws ValidationException if validation fails
     */
    public UCSClient parseAndValidate(String jsonString) throws ValidationException {
        ValidationResult jsonValidation = validateJson(jsonString);
        if (!jsonValidation.isValid()) {
            throw new ValidationException("JSON validation failed: " + jsonValidation.getErrorMessage());
        }

        try {
            UCSClient client = objectMapper.readValue(jsonString, UCSClient.class);
            ValidationResult objectValidation = validate(client);
            
            if (!objectValidation.isValid()) {
                throw new ValidationException("Object validation failed: " + objectValidation.getErrorMessage());
            }
            
            return client;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Failed to parse UCS Client JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Result of a validation operation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
