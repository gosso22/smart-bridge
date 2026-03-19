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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UCSToFHIRTransformer.
 * Tests identifier mapping, gender normalization, and demographic field mapping
 * using the flat OpenSRP Client structure.
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
        UCSClient ucsClient = createValidUCSClient();

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);

        assertNotNull(wrapper);
        assertEquals("UCS", wrapper.getSourceSystem());
        assertEquals("OPENSRP-12345", wrapper.getOriginalId());

        Patient patient = (Patient) wrapper.getResource();
        assertNotNull(patient);
    }

    @Test
    void testIdentifierMapping_OpensrpId() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        Identifier opensrpIdentifier = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/opensrp-id".equals(id.getSystem()))
            .findFirst()
            .orElse(null);

        assertNotNull(opensrpIdentifier);
        assertEquals("OPENSRP-12345", opensrpIdentifier.getValue());
    }

    @Test
    void testIdentifierMapping_NationalId() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        Identifier nationalIdentifier = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/national-id".equals(id.getSystem()))
            .findFirst()
            .orElse(null);

        assertNotNull(nationalIdentifier);
        assertEquals("NAT-67890", nationalIdentifier.getValue());
    }

    @Test
    void testIdentifierMapping_BaseEntityId() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        Identifier baseEntityIdentifier = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/base-entity-id".equals(id.getSystem()))
            .findFirst()
            .orElse(null);

        assertNotNull(baseEntityIdentifier);
        assertEquals("base-entity-001", baseEntityIdentifier.getValue());
    }

    @Test
    void testGenderNormalization_Male() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setGender("Male");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertEquals(AdministrativeGender.MALE, patient.getGender());
    }

    @Test
    void testGenderNormalization_Female() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setGender("Female");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertEquals(AdministrativeGender.FEMALE, patient.getGender());
    }

    @Test
    void testGenderNormalization_SingleLetter_M() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setGender("M");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertEquals(AdministrativeGender.MALE, patient.getGender());
    }

    @Test
    void testGenderNormalization_SingleLetter_F() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setGender("F");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertEquals(AdministrativeGender.FEMALE, patient.getGender());
    }

    @Test
    void testGenderNormalization_Other() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setGender("O");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertEquals(AdministrativeGender.OTHER, patient.getGender());
    }

    @Test
    void testGenderNormalization_Null_ThrowsException() {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setGender(null);

        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testNameMapping() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertFalse(patient.getName().isEmpty());
        HumanName name = patient.getName().get(0);
        assertEquals("Doe", name.getFamily());
        assertEquals("John", name.getGiven().get(0).getValue());
    }

    @Test
    void testBirthDateMapping_ISODatetime() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setBirthdate("1990-05-15T00:00:00.000+0300");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertNotNull(patient.getBirthDate());
    }

    @Test
    void testBirthDateMapping_PlainDate() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setBirthdate("1990-05-15");

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertNotNull(patient.getBirthDate());
    }

    @Test
    void testAddressMapping() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();

        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        assertFalse(patient.getAddress().isEmpty());
        Address address = patient.getAddress().get(0);
        assertEquals("Tanzania", address.getCountry());
        assertEquals("Dar es Salaam", address.getState());
        assertEquals("Ilala", address.getDistrict());
        assertEquals("Kariakoo", address.getCity());
    }

    @Test
    void testTransformUCSToFHIR_NullClient_ThrowsException() {
        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(null);
        });
    }

    @Test
    void testTransformUCSToFHIR_MissingIdentifiers_UsesBaseEntityId() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setIdentifiers(null);

        // Should still work because baseEntityId is set
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        assertNotNull(wrapper);
    }

    @Test
    void testTransformUCSToFHIR_MissingBothIds_ThrowsException() {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setIdentifiers(null);
        ucsClient.setBaseEntityId(null);

        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testTransformUCSToFHIR_MissingName_ThrowsException() {
        UCSClient ucsClient = createValidUCSClient();
        ucsClient.setFirstName(null);

        assertThrows(TransformationException.class, () -> {
            transformer.transformUCSToFHIR(ucsClient);
        });
    }

    @Test
    void testValidateUCSClient_Valid() {
        UCSClient ucsClient = createValidUCSClient();

        boolean isValid = transformer.validateUCSClient(ucsClient);

        assertTrue(isValid);
    }

    @Test
    void testValidateUCSClient_Null() {
        boolean isValid = transformer.validateUCSClient(null);

        assertFalse(isValid);
    }

    @Test
    void testValidateFHIRResource_Valid() throws TransformationException {
        UCSClient ucsClient = createValidUCSClient();
        FHIRResourceWrapper<?> wrapper = transformer.transformUCSToFHIR(ucsClient);
        Patient patient = (Patient) wrapper.getResource();

        boolean isValid = transformer.validateFHIRResource(patient);

        assertTrue(isValid);
    }

    @Test
    void testValidateFHIRResource_Null() {
        boolean isValid = transformer.validateFHIRResource(null);

        assertFalse(isValid);
    }

    @Test
    void testParseBirthdate_PlainDate() {
        assertEquals(java.time.LocalDate.of(1990, 5, 15),
            UCSToFHIRTransformer.parseBirthdate("1990-05-15"));
    }

    @Test
    void testParseBirthdate_ISODatetime() {
        assertEquals(java.time.LocalDate.of(1990, 5, 15),
            UCSToFHIRTransformer.parseBirthdate("1990-05-15T00:00:00.000+0300"));
    }

    @Test
    void testParseBirthdate_Null() {
        assertNull(UCSToFHIRTransformer.parseBirthdate(null));
    }

    @Test
    void testParseBirthdate_Empty() {
        assertNull(UCSToFHIRTransformer.parseBirthdate(""));
    }

    // Helper method to create a valid OpenSRP-style UCS Client
    private UCSClient createValidUCSClient() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("base-entity-001");
        client.setType("Client");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "OPENSRP-12345");
        client.setIdentifiers(identifiers);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("national_id", "NAT-67890");
        client.setAttributes(attributes);

        client.setFirstName("John");
        client.setLastName("Doe");
        client.setGender("Male");
        client.setBirthdate("1990-05-15T00:00:00.000+0300");

        UCSClient.OpenSRPAddress address = new UCSClient.OpenSRPAddress();
        address.setCountry("Tanzania");
        address.setStateProvince("Dar es Salaam");
        address.setCountyDistrict("Ilala");
        address.setCityVillage("Kariakoo");
        client.setAddresses(List.of(address));

        client.setServerVersion(1567890123456L);

        return client;
    }
}
