package com.smartbridge.core.validation;

import com.smartbridge.core.model.fhir.FHIRResourceBuilder;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FHIR validator
 */
class FHIRValidatorTest {

    private FHIRValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FHIRValidator();
    }

    @Test
    void testValidatePatient_validResource() {
        Patient patient = FHIRResourceBuilder.patient()
            .withIdentifier("http://example.org", "12345")
            .withName("John", "Doe")
            .withGender("M")
            .withBirthDate(LocalDate.of(1990, 1, 1))
            .build();

        FHIRValidator.FHIRValidationResult result = validator.validate(patient);
        
        assertTrue(result.isValid(), "Valid patient should pass validation");
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidateObservation_validResource() {
        Observation observation = FHIRResourceBuilder.observation()
            .withSubject("Patient/123")
            .withCode("http://loinc.org", "8867-4", "Heart rate")
            .withValueQuantity(72.0, "beats/min", "http://unitsofmeasure.org", "/min")
            .withStatus(Observation.ObservationStatus.FINAL)
            .build();

        FHIRValidator.FHIRValidationResult result = validator.validate(observation);
        
        assertTrue(result.isValid(), "Valid observation should pass validation");
    }

    @Test
    void testValidateTask_validResource() {
        Task task = FHIRResourceBuilder.task()
            .withStatus(Task.TaskStatus.REQUESTED)
            .withIntent(Task.TaskIntent.ORDER)
            .withFor("Patient/123")
            .build();

        FHIRValidator.FHIRValidationResult result = validator.validate(task);
        
        assertTrue(result.isValid(), "Valid task should pass validation");
    }

    @Test
    void testValidateMedicationRequest_validResource() {
        MedicationRequest medRequest = FHIRResourceBuilder.medicationRequest()
            .withStatus(MedicationRequest.MedicationRequestStatus.ACTIVE)
            .withIntent(MedicationRequest.MedicationRequestIntent.ORDER)
            .withSubject("Patient/123")
            .withMedicationCodeableConcept("http://www.nlm.nih.gov/research/umls/rxnorm", 
                "313782", "Acetaminophen")
            .build();

        FHIRValidator.FHIRValidationResult result = validator.validate(medRequest);
        
        assertTrue(result.isValid(), "Valid medication request should pass validation");
    }

    @Test
    void testValidate_nullResource() {
        FHIRValidator.FHIRValidationResult result = validator.validate(null);
        
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrorMessage().contains("null"));
    }

    @Test
    void testValidateAndThrow_validResource() {
        Patient patient = FHIRResourceBuilder.patient()
            .withIdentifier("http://example.org", "12345")
            .withName("John", "Doe")
            .build();

        assertDoesNotThrow(() -> validator.validateAndThrow(patient));
    }

    @Test
    void testValidateAndThrow_nullResource() {
        assertThrows(ValidationException.class, () -> validator.validateAndThrow(null));
    }

    @Test
    void testValidationResult_toString() {
        FHIRValidator.FHIRValidationResult validResult = FHIRValidator.FHIRValidationResult.valid();
        assertEquals("Valid", validResult.toString());

        FHIRValidator.FHIRValidationResult invalidResult = 
            FHIRValidator.FHIRValidationResult.invalid("Error message");
        assertTrue(invalidResult.toString().contains("Invalid"));
        assertTrue(invalidResult.toString().contains("Error message"));
    }
}
