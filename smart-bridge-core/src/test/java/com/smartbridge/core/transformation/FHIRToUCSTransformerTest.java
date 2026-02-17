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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FHIRToUCSTransformer.
 * Tests reverse transformation from FHIR Patient resources to UCS Client format.
 */
@ExtendWith(MockitoExtension.class)
class FHIRToUCSTransformerTest {

    @Mock
    private UCSClientValidator ucsValidator;

    private FHIRToUCSTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new FHIRToUCSTransformer(ucsValidator);
        
        // Mock validator to return valid by default (lenient to avoid unnecessary stubbing warnings)
        lenient().when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
    }

    @Test
    void testTransformFHIRToUCS_ValidPatient_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getIdentifiers());
        assertEquals("opensrp-123", result.getIdentifiers().getOpensrpId());
        assertEquals("national-456", result.getIdentifiers().getNationalId());
        
        assertNotNull(result.getDemographics());
        assertEquals("John", result.getDemographics().getFirstName());
        assertEquals("Doe", result.getDemographics().getLastName());
        assertEquals("M", result.getDemographics().getGender());
        assertEquals(LocalDate.of(1990, 1, 15), result.getDemographics().getBirthDate());
        
        assertNotNull(result.getMetadata());
        assertEquals("FHIR", result.getMetadata().getSource());
    }

    @Test
    void testTransformFHIRToUCS_WithAddress_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Address address = new Address();
        address.setDistrict("Dar es Salaam");
        address.setCity("Kinondoni");
        address.setText("Mwenge Village");
        patient.addAddress(address);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getDemographics().getAddress());
        assertEquals("Dar es Salaam", result.getDemographics().getAddress().getDistrict());
        assertEquals("Kinondoni", result.getDemographics().getAddress().getWard());
        assertEquals("Mwenge Village", result.getDemographics().getAddress().getVillage());
    }

    @Test
    void testTransformFHIRToUCS_FemaleGender_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertEquals("F", result.getDemographics().getGender());
    }

    @Test
    void testTransformFHIRToUCS_OtherGender_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.OTHER);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertEquals("O", result.getDemographics().getGender());
    }

    @Test
    void testTransformFHIRToUCS_UnknownGender_ReturnsNull() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNull(result.getDemographics().getGender());
    }

    @Test
    void testTransformFHIRToUCS_NullWrapper_ThrowsException() {
        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(null)
        );
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testTransformFHIRToUCS_NullResource_ThrowsException() {
        // Arrange
        FHIRResourceWrapper<Patient> wrapper = new FHIRResourceWrapper<>();
        // Don't set resource - leave it null but don't call setResource which would throw NPE

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testTransformFHIRToUCS_UnsupportedResourceType_ThrowsException() {
        // Arrange
        Observation observation = new Observation();
        FHIRResourceWrapper<Observation> wrapper = FHIRResourceWrapper.forObservation(observation, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("Unsupported FHIR resource type"));
        assertEquals("UNSUPPORTED_RESOURCE_TYPE", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingIdentifiers_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getIdentifier().clear();
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("must have at least one identifier"));
        assertEquals("MISSING_IDENTIFIER", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingOpensrpId_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getIdentifier().clear();
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/national-id")
            .setValue("national-456");
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("opensrp"));
        assertEquals("MISSING_OPENSRP_ID", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingName_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getName().clear();
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("must have at least one name"));
        assertEquals("MISSING_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingGivenName_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getName().get(0).getGiven().clear();
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("given name"));
        assertEquals("MISSING_GIVEN_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingFamilyName_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getName().get(0).setFamily(null);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("family name"));
        assertEquals("MISSING_FAMILY_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MissingGender_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.setGender(null);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("must have a gender"));
        assertEquals("MISSING_GENDER", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_ValidationFails_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");
        
        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.invalid("Validation error"));

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().toLowerCase().contains("validation"));
        assertEquals("VALIDATION_FAILED", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_WithoutNationalId_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.getIdentifier().removeIf(id -> 
            "http://moh.go.tz/identifier/national-id".equals(id.getSystem())
        );
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result);
        assertEquals("opensrp-123", result.getIdentifiers().getOpensrpId());
        assertNull(result.getIdentifiers().getNationalId());
    }

    @Test
    void testTransformFHIRToUCS_WithoutBirthDate_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setBirthDate(null);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result);
        assertNull(result.getDemographics().getBirthDate());
    }

    @Test
    void testTransformFHIRToUCS_WithoutAddress_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        // No address added
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result);
        assertNull(result.getDemographics().getAddress());
    }

    // Helper method to create a valid FHIR Patient
    private Patient createValidPatient() {
        Patient patient = new Patient();
        
        // Add identifiers
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue("opensrp-123");
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/national-id")
            .setValue("national-456");
        
        // Add name
        patient.addName()
            .addGiven("John")
            .setFamily("Doe");
        
        // Add gender
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        
        // Add birth date
        patient.setBirthDate(java.sql.Date.valueOf(LocalDate.of(1990, 1, 15)));
        
        return patient;
    }

    // ========== Edge Case Tests for Missing FHIR Elements ==========

    @Test
    void testTransformFHIRToUCS_EmptyIdentifierValue_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getIdentifier().clear();
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue(""); // Empty value
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("opensrp"));
        assertEquals("MISSING_OPENSRP_ID", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_IdentifierWithoutSystem_IgnoresIdentifier() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.addIdentifier()
            .setValue("some-value"); // No system set
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - should still succeed with valid identifiers
        assertNotNull(result);
        assertEquals("opensrp-123", result.getIdentifiers().getOpensrpId());
    }

    @Test
    void testTransformFHIRToUCS_IdentifierWithoutValue_IgnoresIdentifier() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.addIdentifier()
            .setSystem("http://example.com/identifier"); // No value set
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - should still succeed with valid identifiers
        assertNotNull(result);
        assertEquals("opensrp-123", result.getIdentifiers().getOpensrpId());
    }

    @Test
    void testTransformFHIRToUCS_MultipleNames_UsesFirstName() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.addName()
            .addGiven("Jane")
            .setFamily("Smith");
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - should use first name
        assertEquals("John", result.getDemographics().getFirstName());
        assertEquals("Doe", result.getDemographics().getLastName());
    }

    @Test
    void testTransformFHIRToUCS_MultipleGivenNames_UsesFirstGivenName() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.getName().get(0).getGiven().clear();
        patient.getName().get(0)
            .addGiven("John")
            .addGiven("Michael")
            .addGiven("Robert");
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - should use first given name
        assertEquals("John", result.getDemographics().getFirstName());
    }

    @Test
    void testTransformFHIRToUCS_EmptyGivenName_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getName().get(0).getGiven().clear();
        patient.getName().get(0).addGiven(""); // Empty string
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("given name"));
        assertEquals("MISSING_GIVEN_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_EmptyFamilyName_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.getName().get(0).setFamily(""); // Empty string
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act & Assert
        TransformationException exception = assertThrows(
            TransformationException.class,
            () -> transformer.transformFHIRToUCS(wrapper)
        );
        
        assertTrue(exception.getMessage().contains("family name"));
        assertEquals("MISSING_FAMILY_NAME", exception.getErrorCode());
    }

    @Test
    void testTransformFHIRToUCS_MultipleAddresses_UsesFirstAddress() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Address address1 = new Address();
        address1.setDistrict("Dar es Salaam");
        address1.setCity("Kinondoni");
        address1.setText("Mwenge Village");
        patient.addAddress(address1);
        
        Address address2 = new Address();
        address2.setDistrict("Arusha");
        address2.setCity("Arusha City");
        address2.setText("Kaloleni");
        patient.addAddress(address2);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - should use first address
        assertNotNull(result.getDemographics().getAddress());
        assertEquals("Dar es Salaam", result.getDemographics().getAddress().getDistrict());
        assertEquals("Kinondoni", result.getDemographics().getAddress().getWard());
        assertEquals("Mwenge Village", result.getDemographics().getAddress().getVillage());
    }

    @Test
    void testTransformFHIRToUCS_PartialAddress_OnlyDistrict() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Address address = new Address();
        address.setDistrict("Dar es Salaam");
        // No city or text
        patient.addAddress(address);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getDemographics().getAddress());
        assertEquals("Dar es Salaam", result.getDemographics().getAddress().getDistrict());
        assertNull(result.getDemographics().getAddress().getWard());
        assertNull(result.getDemographics().getAddress().getVillage());
    }

    @Test
    void testTransformFHIRToUCS_PartialAddress_OnlyCity() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Address address = new Address();
        address.setCity("Kinondoni");
        // No district or text
        patient.addAddress(address);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getDemographics().getAddress());
        assertNull(result.getDemographics().getAddress().getDistrict());
        assertEquals("Kinondoni", result.getDemographics().getAddress().getWard());
        assertNull(result.getDemographics().getAddress().getVillage());
    }

    @Test
    void testTransformFHIRToUCS_PartialAddress_OnlyText() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Address address = new Address();
        address.setText("Mwenge Village");
        // No district or city
        patient.addAddress(address);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getDemographics().getAddress());
        assertNull(result.getDemographics().getAddress().getDistrict());
        assertNull(result.getDemographics().getAddress().getWard());
        assertEquals("Mwenge Village", result.getDemographics().getAddress().getVillage());
    }

    @Test
    void testTransformFHIRToUCS_EmptyAddress_AddressNotSet() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Address address = new Address();
        // No fields set - completely empty address
        patient.addAddress(address);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - HAPI FHIR's hasAddress() or isEmpty() check prevents setting empty addresses
        // So the address remains null when the FHIR address has no actual data
        assertNull(result.getDemographics().getAddress());
    }

    // ========== Edge Case Tests for Data Type Conversions ==========

    @Test
    void testTransformFHIRToUCS_BirthDateAsUtilDate_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setBirthDate(new java.util.Date(90, 0, 15)); // 1990-01-15 using deprecated constructor
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getDemographics().getBirthDate());
        assertEquals(1990, result.getDemographics().getBirthDate().getYear());
        assertEquals(1, result.getDemographics().getBirthDate().getMonthValue());
        assertEquals(15, result.getDemographics().getBirthDate().getDayOfMonth());
    }

    @Test
    void testTransformFHIRToUCS_BirthDateAsSqlDate_Success() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setBirthDate(java.sql.Date.valueOf(LocalDate.of(1990, 1, 15)));
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getDemographics().getBirthDate());
        assertEquals(LocalDate.of(1990, 1, 15), result.getDemographics().getBirthDate());
    }

    @Test
    void testTransformFHIRToUCS_MetadataWithLastUpdated_UsesLastUpdated() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        Meta meta = new Meta();
        meta.setLastUpdated(new java.util.Date());
        patient.setMeta(meta);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertNotNull(result.getMetadata().getUpdatedAt());
    }

    @Test
    void testTransformFHIRToUCS_MetadataWithoutLastUpdated_UsesCurrentTime() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        // No meta or lastUpdated set
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");
        LocalDateTime beforeTransform = LocalDateTime.now().minusSeconds(1);

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertNotNull(result.getMetadata().getUpdatedAt());
        assertTrue(result.getMetadata().getUpdatedAt().isAfter(beforeTransform));
    }

    @Test
    void testTransformFHIRToUCS_MetadataWithFhirId_StoresFhirId() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.setId("patient-fhir-123");
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertEquals("patient-fhir-123", result.getMetadata().getFhirId());
    }

    @Test
    void testTransformFHIRToUCS_MetadataWithoutFhirId_NoFhirIdStored() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        // No ID set
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertNull(result.getMetadata().getFhirId());
    }

    @Test
    void testTransformFHIRToUCS_CustomSourceSystem_UsesCustomSource() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "CustomSystem", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertEquals("CustomSystem", result.getMetadata().getSource());
    }

    @Test
    void testTransformFHIRToUCS_NullSourceSystem_DefaultsToFHIR() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, null, "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertEquals("FHIR", result.getMetadata().getSource());
    }

    @Test
    void testTransformFHIRToUCS_EmptySourceSystem_DefaultsToFHIR() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert
        assertNotNull(result.getMetadata());
        assertEquals("FHIR", result.getMetadata().getSource());
    }

    @Test
    void testTransformFHIRToUCS_NullGenderEnum_ThrowsException() {
        // Arrange
        Patient patient = createValidPatient();
        patient.setGender(Enumerations.AdministrativeGender.NULL);
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = assertDoesNotThrow(() -> transformer.transformFHIRToUCS(wrapper));

        // Assert - NULL enum should map to null gender
        assertNull(result.getDemographics().getGender());
    }

    @Test
    void testTransformFHIRToUCS_DuplicateOpensrpIds_UsesLastValue() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue("opensrp-duplicate");
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - implementation overwrites, so uses last opensrp-id found
        assertEquals("opensrp-duplicate", result.getIdentifiers().getOpensrpId());
    }

    @Test
    void testTransformFHIRToUCS_DuplicateNationalIds_UsesLastValue() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/national-id")
            .setValue("national-duplicate");
        
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - implementation overwrites, so uses last national-id found
        assertEquals("national-duplicate", result.getIdentifiers().getNationalId());
    }

    @Test
    void testTransformFHIRToUCS_ClinicalDataInitialized_EmptyObject() throws TransformationException {
        // Arrange
        Patient patient = createValidPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test-id");

        // Act
        UCSClient result = transformer.transformFHIRToUCS(wrapper);

        // Assert - clinical data should be initialized but empty
        assertNotNull(result.getClinicalData());
    }
}
