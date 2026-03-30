package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class FHIRToCHTPersonTransformerTest {

    private FHIRToCHTPersonTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new FHIRToCHTPersonTransformer();
    }

    @Test
    void testTransform_ValidPatient() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertNotNull(contact);
        assertEquals("cht-person-001", contact.getId());
        assertEquals("12345", contact.getPatientId());
        assertEquals("Amina Juma", contact.getName());
        assertEquals("female", contact.getSex());
        assertEquals("contact", contact.getType());
        assertEquals("person", contact.getContactType());
    }

    @Test
    void testTransform_IdentifierExtraction() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("cht-person-001", contact.getId());
        assertEquals("12345", contact.getPatientId());
    }

    @Test
    void testTransform_NameConcatenation() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("Amina Juma", contact.getName());
    }

    @Test
    void testTransform_NameGivenOnly() throws TransformationException {
        Patient patient = createValidPatient();
        patient.getName().get(0).setFamily(null);
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("Amina", contact.getName());
    }

    @Test
    void testTransform_GenderMale() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setGender(AdministrativeGender.MALE);
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("male", contact.getSex());
    }

    @Test
    void testTransform_GenderFemale() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("female", contact.getSex());
    }

    @Test
    void testTransform_GenderUnknown() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setGender(AdministrativeGender.UNKNOWN);
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        CHTContact contact = transformer.transform(wrapper);

        // CHT only uses male/female; unknown leaves sex null
        assertNull(contact.getSex());
    }

    @Test
    void testTransform_BirthDate() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertNotNull(contact.getDateOfBirth());
        assertTrue(contact.getDateOfBirth().startsWith("1985-06-15"));
    }

    @Test
    void testTransform_BirthDate_NotSet() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setBirthDate(null);
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        CHTContact contact = transformer.transform(wrapper);

        assertNull(contact.getDateOfBirth());
    }

    @Test
    void testTransform_Phone() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("+255712345678", contact.getPhone());
    }

    @Test
    void testTransform_Phone_NotSet() throws TransformationException {
        Patient patient = createValidPatient();
        patient.getTelecom().clear();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        CHTContact contact = transformer.transform(wrapper);

        assertNull(contact.getPhone());
    }

    @Test
    void testTransform_ParentMapping() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertNotNull(contact.getParent());
        assertEquals("cht-clinic-001", contact.getParent().getId());
    }

    @Test
    void testTransform_ParentMapping_NotSet() throws TransformationException {
        Patient patient = createValidPatient();
        patient.setManagingOrganization(null);
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        CHTContact contact = transformer.transform(wrapper);

        assertNull(contact.getParent());
    }

    @Test
    void testTransform_NullWrapper() {
        assertThrows(TransformationException.class, () -> transformer.transform(null));
    }

    @Test
    void testTransform_NullResource() {
        FHIRResourceWrapper<Patient> wrapper = new FHIRResourceWrapper<>();

        assertThrows(TransformationException.class, () -> transformer.transform(wrapper));
    }

    @Test
    void testTransform_UnsupportedResourceType() {
        Observation observation = new Observation();
        observation.setId("obs-001");
        FHIRResourceWrapper<Observation> wrapper = FHIRResourceWrapper.forObservation(observation, "FHIR", "test");

        TransformationException ex = assertThrows(TransformationException.class,
            () -> transformer.transform(wrapper));
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    @Test
    void testTransform_MissingIdentifiers() {
        Patient patient = createValidPatient();
        patient.getIdentifier().clear();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        assertThrows(TransformationException.class, () -> transformer.transform(wrapper));
    }

    @Test
    void testTransform_MissingCHTUUID() {
        Patient patient = createValidPatient();
        // Remove CHT UUID, keep only patient_id
        patient.getIdentifier().removeIf(i ->
            FHIRToCHTPersonTransformer.CHT_UUID_SYSTEM.equals(i.getSystem()));
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        TransformationException ex = assertThrows(TransformationException.class,
            () -> transformer.transform(wrapper));
        assertTrue(ex.getMessage().contains("CHT UUID"));
    }

    @Test
    void testTransform_MissingName() {
        Patient patient = createValidPatient();
        patient.getName().clear();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", "test");

        assertThrows(TransformationException.class, () -> transformer.transform(wrapper));
    }

    @Test
    void testTransform_TypeFields() throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = createValidWrapper();

        CHTContact contact = transformer.transform(wrapper);

        assertEquals("contact", contact.getType());
        assertEquals("person", contact.getContactType());
    }

    // Helpers

    private FHIRResourceWrapper<Patient> createValidWrapper() {
        Patient patient = createValidPatient();
        return FHIRResourceWrapper.forPatient(patient, "FHIR", "test");
    }

    private Patient createValidPatient() {
        Patient patient = new Patient();
        patient.setId("fhir-patient-001");

        // Identifiers
        patient.addIdentifier(new Identifier()
            .setSystem(FHIRToCHTPersonTransformer.CHT_UUID_SYSTEM)
            .setValue("cht-person-001"));
        patient.addIdentifier(new Identifier()
            .setSystem(FHIRToCHTPersonTransformer.CHT_PATIENT_ID_SYSTEM)
            .setValue("12345"));

        // Name
        HumanName name = new HumanName();
        name.addGiven("Amina");
        name.setFamily("Juma");
        patient.addName(name);

        // Gender
        patient.setGender(AdministrativeGender.FEMALE);

        // Birth date
        LocalDate birthDate = LocalDate.of(1985, 6, 15);
        Date date = Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        patient.setBirthDate(date);

        // Phone
        ContactPoint phone = new ContactPoint();
        phone.setSystem(ContactPoint.ContactPointSystem.PHONE);
        phone.setValue("+255712345678");
        patient.addTelecom(phone);

        // Managing organization
        patient.setManagingOrganization(new Reference("Organization/cht-clinic-001"));

        return patient;
    }
}
