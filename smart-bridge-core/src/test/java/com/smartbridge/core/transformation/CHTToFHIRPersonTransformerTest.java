package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CHTToFHIRPersonTransformerTest {

    private CHTToFHIRPersonTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CHTToFHIRPersonTransformer();
    }

    @Test
    void testTransform_ValidContact() throws TransformationException {
        CHTContact contact = createValidContact();

        FHIRResourceWrapper<Patient> result = transformer.transform(contact);

        assertNotNull(result);
        assertEquals("CHT", result.getSourceSystem());
        assertEquals("cht-person-001", result.getOriginalId());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.PATIENT, result.getResourceType());

        Patient patient = result.getResource();
        assertNotNull(patient);
    }

    @Test
    void testTransform_IdentifierMapping() throws TransformationException {
        CHTContact contact = createValidContact();

        Patient patient = transformer.transform(contact).getResource();

        assertEquals(2, patient.getIdentifier().size());

        // CHT UUID
        String chtUuid = patient.getIdentifier().stream()
            .filter(i -> CHTToFHIRPersonTransformer.CHT_UUID_SYSTEM.equals(i.getSystem()))
            .findFirst().map(i -> i.getValue()).orElse(null);
        assertEquals("cht-person-001", chtUuid);

        // CHT patient_id
        String patientId = patient.getIdentifier().stream()
            .filter(i -> CHTToFHIRPersonTransformer.CHT_PATIENT_ID_SYSTEM.equals(i.getSystem()))
            .findFirst().map(i -> i.getValue()).orElse(null);
        assertEquals("12345", patientId);
    }

    @Test
    void testTransform_IdentifierMapping_NoPatientId() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setPatientId(null);

        Patient patient = transformer.transform(contact).getResource();

        assertEquals(1, patient.getIdentifier().size());
        assertEquals(CHTToFHIRPersonTransformer.CHT_UUID_SYSTEM, patient.getIdentifier().get(0).getSystem());
    }

    @Test
    void testTransform_NameParsing_TwoWords() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setName("Amina Juma");

        Patient patient = transformer.transform(contact).getResource();

        assertEquals("Amina", patient.getName().get(0).getGivenAsSingleString());
        assertEquals("Juma", patient.getName().get(0).getFamily());
    }

    @Test
    void testTransform_NameParsing_ThreeWords() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setName("Amina Hassan Juma");

        Patient patient = transformer.transform(contact).getResource();

        assertEquals("Amina", patient.getName().get(0).getGivenAsSingleString());
        assertEquals("Hassan Juma", patient.getName().get(0).getFamily());
    }

    @Test
    void testTransform_NameParsing_SingleWord() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setName("Amina");

        Patient patient = transformer.transform(contact).getResource();

        assertEquals("Amina", patient.getName().get(0).getGivenAsSingleString());
        assertEquals("Amina", patient.getName().get(0).getFamily());
    }

    @Test
    void testTransform_GenderMale() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setSex("male");

        Patient patient = transformer.transform(contact).getResource();

        assertEquals(AdministrativeGender.MALE, patient.getGender());
    }

    @Test
    void testTransform_GenderFemale() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setSex("female");

        Patient patient = transformer.transform(contact).getResource();

        assertEquals(AdministrativeGender.FEMALE, patient.getGender());
    }

    @Test
    void testTransform_GenderNull() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setSex(null);

        Patient patient = transformer.transform(contact).getResource();

        assertEquals(AdministrativeGender.UNKNOWN, patient.getGender());
    }

    @Test
    void testTransform_BirthDate() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setDateOfBirth("1985-06-15");

        Patient patient = transformer.transform(contact).getResource();

        assertNotNull(patient.getBirthDate());
    }

    @Test
    void testTransform_BirthDate_Null() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setDateOfBirth(null);

        Patient patient = transformer.transform(contact).getResource();

        assertNull(patient.getBirthDate());
    }

    @Test
    void testTransform_BirthDate_Invalid() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setDateOfBirth("not-a-date");

        Patient patient = transformer.transform(contact).getResource();

        // Invalid date should be silently ignored
        assertNull(patient.getBirthDate());
    }

    @Test
    void testTransform_Phone() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setPhone("+255712345678");

        Patient patient = transformer.transform(contact).getResource();

        assertEquals(1, patient.getTelecom().size());
        ContactPoint telecom = patient.getTelecom().get(0);
        assertEquals(ContactPoint.ContactPointSystem.PHONE, telecom.getSystem());
        assertEquals("+255712345678", telecom.getValue());
        assertEquals(ContactPoint.ContactPointUse.MOBILE, telecom.getUse());
    }

    @Test
    void testTransform_Phone_Null() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setPhone(null);

        Patient patient = transformer.transform(contact).getResource();

        assertTrue(patient.getTelecom().isEmpty());
    }

    @Test
    void testTransform_ParentMapping() throws TransformationException {
        CHTContact contact = createValidContact();
        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("cht-clinic-001");
        contact.setParent(parent);

        Patient patient = transformer.transform(contact).getResource();

        assertTrue(patient.hasManagingOrganization());
        assertEquals("Organization/cht-clinic-001", patient.getManagingOrganization().getReference());
    }

    @Test
    void testTransform_ParentMapping_NoParent() throws TransformationException {
        CHTContact contact = createValidContact();
        contact.setParent(null);

        Patient patient = transformer.transform(contact).getResource();

        assertFalse(patient.hasManagingOrganization());
    }

    @Test
    void testTransform_NullContact() {
        assertThrows(TransformationException.class, () -> transformer.transform(null));
    }

    @Test
    void testTransform_MissingId() {
        CHTContact contact = createValidContact();
        contact.setId(null);

        TransformationException ex = assertThrows(TransformationException.class,
            () -> transformer.transform(contact));
        assertTrue(ex.getMessage().contains("_id"));
    }

    @Test
    void testTransform_EmptyId() {
        CHTContact contact = createValidContact();
        contact.setId("");

        assertThrows(TransformationException.class, () -> transformer.transform(contact));
    }

    @Test
    void testTransform_MissingName() {
        CHTContact contact = createValidContact();
        contact.setName(null);

        TransformationException ex = assertThrows(TransformationException.class,
            () -> transformer.transform(contact));
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void testTransform_EmptyName() {
        CHTContact contact = createValidContact();
        contact.setName("");

        assertThrows(TransformationException.class, () -> transformer.transform(contact));
    }

    // Helper
    private CHTContact createValidContact() {
        CHTContact contact = new CHTContact();
        contact.setId("cht-person-001");
        contact.setRev("1-abc123");
        contact.setType("contact");
        contact.setContactType("person");
        contact.setName("Amina Juma");
        contact.setDateOfBirth("1985-06-15");
        contact.setSex("female");
        contact.setPhone("+255712345678");
        contact.setPatientId("12345");
        contact.setReportedDate(1680000000000L);
        return contact;
    }
}
