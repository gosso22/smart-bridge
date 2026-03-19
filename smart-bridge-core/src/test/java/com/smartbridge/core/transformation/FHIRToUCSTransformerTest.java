package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.validation.UCSClientValidator;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FHIRToUCSTransformer.
 * Tests reverse transformation from FHIR Patient to flat OpenSRP Client format.
 */
@ExtendWith(MockitoExtension.class)
class FHIRToUCSTransformerTest {

    @Mock
    private UCSClientValidator ucsValidator;

    private FHIRToUCSTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new FHIRToUCSTransformer(ucsValidator);

        lenient().when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
    }

    @Test
    void testTransformFHIRToUCS_ValidPatient_Success() throws TransformationException {
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNotNull(result);
        assertEquals("opensrp-123", result.getOpensrpId());
        assertEquals("opensrp-123", result.getBaseEntityId());
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals("Male", result.getGender());
        assertEquals(LocalDate.of(1990, 1, 15).toString(), result.getBirthdate());
    }

    @Test
    void testTransformFHIRToUCS_NationalId_InAttributes() throws TransformationException {
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNotNull(result.getAttributes());
        assertEquals("national-456", result.getAttributes().get("national_id"));
    }

    @Test
    void testTransformFHIRToUCS_WithAddress_Success() throws TransformationException {
        Patient patient = createValidPatient();
        Address address = new Address();
        address.setCountry("Tanzania");
        address.setState("Dar es Salaam");
        address.setDistrict("Ilala");
        address.setCity("Kariakoo");
        address.setText("Mtaa wa Kariakoo");
        patient.addAddress(address);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNotNull(result.getAddresses());
        assertFalse(result.getAddresses().isEmpty());
        UCSClient.OpenSRPAddress addr = result.getAddresses().get(0);
        assertEquals("Tanzania", addr.getCountry());
        assertEquals("Dar es Salaam", addr.getStateProvince());
        assertEquals("Ilala", addr.getCountyDistrict());
        assertEquals("Kariakoo", addr.getCityVillage());
        assertEquals("Mtaa wa Kariakoo", addr.getTown());
    }

    @Test
    void testTransformFHIRToUCS_FemaleGender_Success() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertEquals("Female", result.getGender());
    }

    @Test
    void testTransformFHIRToUCS_OtherGender_Success() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.OTHER);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertEquals("Other", result.getGender());
    }

    @Test
    void testTransformFHIRToUCS_UnknownGender_ReturnsNull() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNull(result.getGender());
    }

    @Test
    void testTransformFHIRToUCS_NullWrapper_ThrowsException() {
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testTransformFHIRToUCS_NullResource_ThrowsException() {
        FHIRResourceWrapper<Patient> wrapper = new FHIRResourceWrapper<>();

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testTransformFHIRToUCS_UnsupportedResourceType_ThrowsException() {
        Observation observation = new Observation();
        FHIRResourceWrapper<Observation> wrapper = FHIRResourceWrapper.forObservation(observation, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertTrue(exception.getMessage().contains("Unsupported FHIR resource type"));
        assertEquals("UNSUPPORTED_RESOURCE_TYPE", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingIdentifiers_ThrowsException() {
        Patient patient = createValidPatient();
        patient.getIdentifier().clear();

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertTrue(exception.getMessage().contains("must have at least one identifier"));
        assertEquals("MISSING_IDENTIFIER", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingOpensrpId_ThrowsException() {
        Patient patient = createValidPatient();
        patient.getIdentifier().clear();
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/national-id")
            .setValue("national-456");

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertTrue(exception.getMessage().contains("opensrp") || exception.getMessage().contains("base-entity"));
        assertEquals("MISSING_OPENSRP_ID", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingName_ThrowsException() {
        Patient patient = createValidPatient();
        patient.getName().clear();

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertEquals("MISSING_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingGivenName_ThrowsException() {
        Patient patient = createValidPatient();
        patient.getName().get(0).getGiven().clear();

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertEquals("MISSING_GIVEN_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingFamilyName_ThrowsException() {
        Patient patient = createValidPatient();
        patient.getName().get(0).setFamily(null);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertEquals("MISSING_FAMILY_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingGender_ThrowsException() {
        Patient patient = createValidPatient();
        patient.setGender(null);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertEquals("MISSING_GENDER", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_ValidationFails_ThrowsException() {
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.invalid("Validation error"));

        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        assertTrue(exception.getMessage().toLowerCase().contains("validation"));
        assertEquals("VALIDATION_FAILED", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_WithoutNationalId_Success() throws TransformationException {
        Patient patient = createValidPatient();
        patient.getIdentifier().removeIf(id ->
            "http://moh.go.tz/identifier/national-id".equals(id.getSystem())
        );

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNotNull(result);
        assertEquals("opensrp-123", result.getOpensrpId());
        assertNull(result.getNationalId());
    }

    @Test
    void testTransformFHIRToUCS_WithoutBirthDate_Success() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setBirthDate(null);

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNotNull(result);
        assertNull(result.getBirthdate());
    }

    @Test
    void testTransformFHIRToUCS_WithoutAddress_Success() throws TransformationException {
        Patient patient = createValidPatient();

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertNotNull(result);
        assertNull(result.getAddresses());
    }

    @Test
    void testTransformFHIRToUCS_TypeIsClient() throws TransformationException {
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertEquals("Client", result.getType());
    }

    @Test
    void testTransformFHIRToUCS_MiddleName_Mapped() throws TransformationException {
        Patient patient = createValidPatient();
        patient.getName().get(0).addGiven("Michael");

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        assertEquals("Michael", result.getMiddleName());
    }

    // Helper
    private Patient createValidPatient() {
        Patient patient = new Patient();

        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue("opensrp-123");
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/national-id")
            .setValue("national-456");

        patient.addName()
            .addGiven("John")
            .setFamily("Doe");

        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.setBirthDate(java.sql.Date.valueOf(LocalDate.of(1990, 1, 15)));

        return patient;
    }
}
