package com.smartbridge.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.smartbridge.core.model.cht.CHTContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for CHT Contact data using JSON Schema validation.
 * Validates CHTContact objects against the defined JSON schema to ensure data integrity.
 */
@Component
public class CHTContactValidator {

    private static final Logger logger = LoggerFactory.getLogger(CHTContactValidator.class);
    private static final String SCHEMA_PATH = "/schemas/cht-contact-schema.json";

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public CHTContactValidator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.schema = loadSchema();
    }

    private JsonSchema loadSchema() {
        try {
            InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH);
            if (schemaStream == null) {
                throw new IllegalStateException("CHT Contact schema not found at: " + SCHEMA_PATH);
            }

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            return factory.getSchema(schemaStream);
        } catch (Exception e) {
            logger.error("Failed to load CHT Contact schema", e);
            throw new IllegalStateException("Failed to initialize CHT Contact validator", e);
        }
    }

    /**
     * Validate a CHTContact object against the JSON schema.
     *
     * @param contact The CHTContact object to validate
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validate(CHTContact contact) {
        if (contact == null) {
            return ValidationResult.invalid("CHT Contact object cannot be null");
        }

        try {
            JsonNode jsonNode = objectMapper.valueToTree(contact);
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            if (errors.isEmpty()) {
                logger.debug("CHT Contact validation successful");
                return ValidationResult.valid();
            } else {
                String errorMessage = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));

                logger.warn("CHT Contact validation failed: {}", errorMessage);
                return ValidationResult.invalid(errorMessage);
            }
        } catch (Exception e) {
            logger.error("Error during CHT Contact validation", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate a JSON string against the CHT Contact schema.
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
                logger.debug("CHT Contact JSON validation successful");
                return ValidationResult.valid();
            } else {
                String errorMessage = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));

                logger.warn("CHT Contact JSON validation failed: {}", errorMessage);
                return ValidationResult.invalid(errorMessage);
            }
        } catch (Exception e) {
            logger.error("Error parsing or validating CHT Contact JSON", e);
            return ValidationResult.invalid("JSON parsing error: " + e.getMessage());
        }
    }

    /**
     * Parse and validate a JSON string, returning a CHTContact object if valid.
     *
     * @param jsonString The JSON string to parse and validate
     * @return The parsed CHTContact object
     * @throws ValidationException if validation fails
     */
    public CHTContact parseAndValidate(String jsonString) throws ValidationException {
        ValidationResult jsonValidation = validateJson(jsonString);
        if (!jsonValidation.isValid()) {
            throw new ValidationException("JSON validation failed: " + jsonValidation.getErrorMessage());
        }

        try {
            CHTContact contact = objectMapper.readValue(jsonString, CHTContact.class);
            ValidationResult objectValidation = validate(contact);

            if (!objectValidation.isValid()) {
                throw new ValidationException("Object validation failed: " + objectValidation.getErrorMessage());
            }

            return contact;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Failed to parse CHT Contact JSON: " + e.getMessage(), e);
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
