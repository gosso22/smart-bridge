package com.smartbridge.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FHIR R4 resource validator using HAPI FHIR validation framework.
 * Validates FHIR resources against R4 specification and business rules.
 */
@Component
public class FHIRValidator {

    private static final Logger logger = LoggerFactory.getLogger(FHIRValidator.class);
    
    private final FhirContext fhirContext;
    private final FhirValidator validator;

    public FHIRValidator() {
        this.fhirContext = FhirContext.forR4();
        this.validator = fhirContext.newValidator();
    }

    /**
     * Validates a FHIR resource against R4 specification
     * 
     * @param resource The FHIR resource to validate
     * @return FHIRValidationResult containing validation outcome
     */
    public FHIRValidationResult validate(Resource resource) {
        if (resource == null) {
            return FHIRValidationResult.invalid("Resource cannot be null");
        }

        try {
            ValidationResult result = validator.validateWithResult(resource);
            
            if (result.isSuccessful()) {
                logger.debug("FHIR resource validation successful: {}", resource.getResourceType());
                return FHIRValidationResult.valid();
            } else {
                List<String> errors = result.getMessages().stream()
                    .map(msg -> msg.getSeverity() + ": " + msg.getMessage())
                    .collect(Collectors.toList());
                
                logger.warn("FHIR resource validation failed for {}: {}", 
                    resource.getResourceType(), errors);
                
                return FHIRValidationResult.invalid(errors);
            }
        } catch (Exception e) {
            logger.error("Error during FHIR validation", e);
            return FHIRValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates a FHIR resource and throws exception if invalid
     * 
     * @param resource The FHIR resource to validate
     * @throws ValidationException if validation fails
     */
    public void validateAndThrow(Resource resource) throws ValidationException {
        FHIRValidationResult result = validate(resource);
        if (!result.isValid()) {
            throw new ValidationException("FHIR validation failed: " + result.getErrorMessage());
        }
    }

    /**
     * Result of FHIR validation
     */
    public static class FHIRValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private FHIRValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static FHIRValidationResult valid() {
            return new FHIRValidationResult(true, List.of());
        }

        public static FHIRValidationResult invalid(String error) {
            return new FHIRValidationResult(false, List.of(error));
        }

        public static FHIRValidationResult invalid(List<String> errors) {
            return new FHIRValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }

        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + getErrorMessage();
        }
    }
}
