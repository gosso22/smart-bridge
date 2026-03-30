package com.smartbridge.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.smartbridge.core.model.cht.CHTReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for CHT Report data using JSON Schema validation.
 * Validates CHTReport objects against the defined JSON schema to ensure data integrity.
 */
@Component
public class CHTReportValidator {

    private static final Logger logger = LoggerFactory.getLogger(CHTReportValidator.class);
    private static final String SCHEMA_PATH = "/schemas/cht-report-schema.json";

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public CHTReportValidator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.schema = loadSchema();
    }

    private JsonSchema loadSchema() {
        try {
            InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH);
            if (schemaStream == null) {
                throw new IllegalStateException("CHT Report schema not found at: " + SCHEMA_PATH);
            }

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            return factory.getSchema(schemaStream);
        } catch (Exception e) {
            logger.error("Failed to load CHT Report schema", e);
            throw new IllegalStateException("Failed to initialize CHT Report validator", e);
        }
    }

    /**
     * Validate a CHTReport object against the JSON schema.
     *
     * @param report The CHTReport object to validate
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validate(CHTReport report) {
        if (report == null) {
            return ValidationResult.invalid("CHT Report object cannot be null");
        }

        try {
            JsonNode jsonNode = objectMapper.valueToTree(report);
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            if (errors.isEmpty()) {
                logger.debug("CHT Report validation successful");
                return ValidationResult.valid();
            } else {
                String errorMessage = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));

                logger.warn("CHT Report validation failed: {}", errorMessage);
                return ValidationResult.invalid(errorMessage);
            }
        } catch (Exception e) {
            logger.error("Error during CHT Report validation", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate a JSON string against the CHT Report schema.
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
                logger.debug("CHT Report JSON validation successful");
                return ValidationResult.valid();
            } else {
                String errorMessage = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));

                logger.warn("CHT Report JSON validation failed: {}", errorMessage);
                return ValidationResult.invalid(errorMessage);
            }
        } catch (Exception e) {
            logger.error("Error parsing or validating CHT Report JSON", e);
            return ValidationResult.invalid("JSON parsing error: " + e.getMessage());
        }
    }

    /**
     * Parse and validate a JSON string, returning a CHTReport object if valid.
     *
     * @param jsonString The JSON string to parse and validate
     * @return The parsed CHTReport object
     * @throws ValidationException if validation fails
     */
    public CHTReport parseAndValidate(String jsonString) throws ValidationException {
        ValidationResult jsonValidation = validateJson(jsonString);
        if (!jsonValidation.isValid()) {
            throw new ValidationException("JSON validation failed: " + jsonValidation.getErrorMessage());
        }

        try {
            CHTReport report = objectMapper.readValue(jsonString, CHTReport.class);
            ValidationResult objectValidation = validate(report);

            if (!objectValidation.isValid()) {
                throw new ValidationException("Object validation failed: " + objectValidation.getErrorMessage());
            }

            return report;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Failed to parse CHT Report JSON: " + e.getMessage(), e);
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
