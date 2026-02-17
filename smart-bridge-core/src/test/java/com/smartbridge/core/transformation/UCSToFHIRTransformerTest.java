package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.validation.FHIRValidator;
import com.smartbridge.core.validation.UCSClientValidator;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UCSToFHIRTransformer.
 * Tests identifier mapping, gender normalization, and demographic field mapping.
 */
@ExtendWith(MockitoExtension.class)
class UCSToFHIRTransformerTest {

    private UCSToFHIRTransformer transformer;
    private UCSClientValidator ucsValidator;
    private FHIRValidator fhirValidator;
    
    @Mock
    private FHIRToUCSTransformer fhirToUCSTransformer;

    @BeforeEach
    void setUp() {
        ucsValidator = new UCSClientValidator();
        fhirValidator = new FHIRValidator();
        transformer = new UCSToFHIRTransformer(ucsValidator, fhirValidator, fhirToUCSTransformer);
    }

    @Test
    void testTransformUCSToFHIR_ValidClient_Success() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);

        // Assert
        assertNotNull(wrapper);
        assertEquals("UCS", wrapper.getSourceSystem());
        assertEquals("OPENSRP-12345", wrapper.getOriginalId());
        
        Patient patient = (Patient) wrapper.getResource();
        assertNotNull(patient);
    }

    @Test
    void testIdentifierMapping_OpensrpId() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        Identifier opensrpIdentifier = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/opensrp-id".equals(id.getSystem()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(opensrpIdentifier);
        assertEquals("OPENSRP-12345", opensrpIdentifier.getValue());
    }

    @Test
    void testIdentifierMapping_NationalId() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        Identifier nationalIdentifier = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/national-id".equals(id.getSystem()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(nationalIdentifier);
        assertEquals("NAT-67890", nationalIdentifier.getValue());
    }

    @Test
    void testGenderNormalization_Male() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.getDemographics().setGender("M");

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        assertEquals(AdministrativeGender.MALE, patient.getGender());
    }

    @Test
    void testGenderNormalization_Female() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.getDemographics().setGender("F");

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        assertEquals(AdministrativeGender.FEMALE, patient.getGender());
    }

    @Test
    void testGenderNormalization_Other() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.getDemographics().setGender("O");

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        assertEquals(AdministrativeGender.OTHER, patient.getGender());
    }

    @Test
    void testGenderNormalization_Null_ThrowsException() {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.getDemographics().setGender(null);

        // Act & Assert - null gender should fail validation
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testNameMapping() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        assertFalse(patient.getName().isEmpty());
        HumanName name = patient.getName().get(0);
        assertEquals("Doe", name.getFamily());
        assertEquals(1, name.getGiven().size());
        assertEquals("John", name.getGiven().get(0).getValue());
    }

    @Test
    void testBirthDateMapping() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        LocalDate birthDate = LocalDate.of(1990, 5, 15);
        ucsClient.getDemographics().setBirthDate(birthDate);

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        assertNotNull(patient.getBirthDate());
    }

    @Test
    void testAddressMapping() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();

        // Act
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Assert
        assertFalse(patient.getAddress().isEmpty());
        Address address = patient.getAddress().get(0);
        assertEquals("Dar es Salaam", address.getDistrict());
        assertEquals("Kinondoni", address.getCity());
        assertEquals("Mwenge", address.getText());
    }

    @Test
    void testTransformUCSToFHIR_NullClient_ThrowsException() {
        // Act & Assert
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(null);
        });
    }

    @Test
    void testTransformUCSToFHIR_MissingIdentifiers_ThrowsException() {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setIdentifiers(null);

        // Act & Assert
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testTransformUCSToFHIR_MissingOpensrpId_ThrowsException() {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.getIdentifiers().setOpensrpId(null);

        // Act & Assert
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testTransformUCSToFHIR_MissingDemographics_ThrowsException() {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setDemographics(null);

        // Act & Assert
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testTransformUCSToFHIR_MissingName_ThrowsException() {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.getDemographics().setFirstName(null);

        // Act & Assert
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testValidateUCSClient_Valid() {
        // Note: validateUCSClient uses JSON schema validation which expects string-formatted dates
        // For Java objects with LocalDate/LocalDateTime, the transformation does field validation instead
        // This test verifies that the method correctly identifies when JSON schema validation fails
        // due to date format mismatch (which is expected for Java objects)
        
        // Arrange
        UCSClient ucsClient = createValidUCSClient();

        // Act
        boolean isValid = transformer.validateUCSClient(ucsClient);

        // Assert - should pass validation with properly configured schema
        assertTrue(isValid);
    }

    @Test
    void testValidateUCSClient_Null() {
        // Act
        boolean isValid = transformer.validateUCSClient(null);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateFHIRResource_Valid() throws TransformationException {
        // Arrange
        UCSClient ucsClient = createValidUCSClient();
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        // Act
        boolean isValid = transformer.validateFHIRResource(patient);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testValidateFHIRResource_Null() {
        // Act
        boolean isValid = transformer.validateFHIRResource(null);

        // Assert
        assertFalse(isValid);
    }

    // Helper method to create a valid UCS Client for testing
    private UCSClient createValidUCSClient() {
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers(
            "OPENSRP-12345",
            "NAT-67890"
        );

        UCSClient.UCSAddress address = new UCSClient.UCSAddress(
            "Dar es Salaam",
            "Kinondoni",
            "Mwenge"
        );

        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics(
            "John",
            "Doe",
            "M",
            LocalDate.of(1990, 5, 15),
            address
        );

        UCSClient.UCSClinicalData clinicalData = new UCSClient.UCSClinicalData(
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList()
        );

        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata(
            LocalDateTime.now(),
            LocalDateTime.now(),
            "UCS",
            null
        );

        return new UCSClient(identifiers, demographics, clinicalData, metadata);
    }
}
