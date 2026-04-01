package com.smartbridge.core.integration;

import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.transformation.CHTToFHIRPersonTransformer;
import com.smartbridge.core.transformation.CHTToFHIRPlaceTransformer;
import com.smartbridge.core.transformation.CHTToFHIRReportTransformer;
import com.smartbridge.core.validation.CHTContactValidator;
import com.smartbridge.core.validation.CHTReportValidator;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-system consistency tests verifying that CHT→FHIR transformation
 * preserves key data fields through the pipeline.
 */
class CHTCrossSystemConsistencyTest {

    private CHTToFHIRPersonTransformer personTransformer;
    private CHTToFHIRPlaceTransformer placeTransformer;
    private CHTToFHIRReportTransformer reportTransformer;
    private CHTContactValidator contactValidator;
    private CHTReportValidator reportValidator;

    @BeforeEach
    void setUp() {
        personTransformer = new CHTToFHIRPersonTransformer();
        placeTransformer = new CHTToFHIRPlaceTransformer();
        reportTransformer = new CHTToFHIRReportTransformer();
        contactValidator = new CHTContactValidator();
        reportValidator = new CHTReportValidator();
    }

    // ==================== Contact → Patient Consistency ====================

    @Test
    void contactToPatient_IdentifierPreservation() throws Exception {
        CHTContact contact = createRealContact();

        FHIRResourceWrapper<Patient> wrapper = personTransformer.transform(contact);
        Patient patient = wrapper.getResource();

        // CHT _id must survive as identifier
        assertTrue(patient.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/cht-uuid".equals(id.getSystem())
                && contact.getId().equals(id.getValue())),
            "CHT _id must be preserved as FHIR identifier");

        // Source system preserved
        assertEquals("CHT", wrapper.getSourceSystem());
        assertEquals(contact.getId(), wrapper.getOriginalId());
    }

    @Test
    void contactToPatient_DemographicPreservation() throws Exception {
        CHTContact contact = createRealContact();

        FHIRResourceWrapper<Patient> wrapper = personTransformer.transform(contact);
        Patient patient = wrapper.getResource();

        // Name preserved
        HumanName name = patient.getName().get(0);
        assertNotNull(name);
        // "Ahmed Ali Hassan" -> given="Ahmed", family="Ali Hassan"
        String fullReconstructed = name.getGiven().get(0).getValue() + " " + name.getFamily();
        assertEquals("Ahmed Ali Hassan", fullReconstructed);

        // Gender preserved
        assertEquals(Enumerations.AdministrativeGender.MALE, patient.getGender());

        // Birth date preserved
        assertNotNull(patient.getBirthDate());

        // Phone preserved
        assertEquals("0777123456", patient.getTelecom().get(0).getValue());
    }

    @Test
    void contactToPatient_HierarchyPreservation() throws Exception {
        CHTContact contact = createRealContact();

        FHIRResourceWrapper<Patient> wrapper = personTransformer.transform(contact);
        Patient patient = wrapper.getResource();

        // Parent CHT place → FHIR managingOrganization
        assertNotNull(patient.getManagingOrganization());
        assertTrue(patient.getManagingOrganization().getReference()
            .contains(contact.getParent().getId()),
            "Parent place ID should be in managingOrganization reference");
    }

    // ==================== Place → Organization Consistency ====================

    @Test
    void placeToOrganization_IdentifierPreservation() throws Exception {
        CHTPlace place = createRealPlace();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = placeTransformer.transform(place);
        Organization org = (Organization) wrappers.get(0).getResource();

        assertTrue(org.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/cht-place-uuid".equals(id.getSystem())
                && place.getId().equals(id.getValue())),
            "CHT place _id must be preserved as Organization identifier");
    }

    @Test
    void placeToOrganization_TypeMappingForClinic() throws Exception {
        CHTPlace place = createRealPlace();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = placeTransformer.transform(place);
        Organization org = (Organization) wrappers.get(0).getResource();

        // type: "clinic" → Organization type "team" (Organizational team)
        assertTrue(org.getType().stream()
            .flatMap(cc -> cc.getCoding().stream())
            .anyMatch(c -> "team".equals(c.getCode())),
            "Clinic type should map to 'team' Organization type");
    }

    @Test
    void placeToOrganization_ParentHierarchyPreservation() throws Exception {
        CHTPlace place = createRealPlace();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = placeTransformer.transform(place);
        Organization org = (Organization) wrappers.get(0).getResource();

        assertNotNull(org.getPartOf());
        assertTrue(org.getPartOf().getReference()
            .contains(place.getParent().getId()),
            "Parent place ID should be in partOf reference");
    }

    @Test
    void placeToOrganization_ContactPersonPreservation() throws Exception {
        CHTPlace place = createRealPlace();
        place.getContact().setName("Ahmed Ali Hassan");

        List<FHIRResourceWrapper<? extends Resource>> wrappers = placeTransformer.transform(place);
        Organization org = (Organization) wrappers.get(0).getResource();

        assertTrue(org.getContact().stream()
            .anyMatch(c -> c.getName() != null && c.getName().getText() != null
                && c.getName().getText().contains("Ahmed")),
            "Contact person name should be preserved");
    }

    // ==================== Report → Encounter Consistency ====================

    @Test
    void reportToEncounter_IdentifierPreservation() throws Exception {
        CHTReport report = createRealReport();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = reportTransformer.transform(report);
        Encounter encounter = (Encounter) wrappers.get(0).getResource();

        assertTrue(encounter.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/cht-report-uuid".equals(id.getSystem())
                && report.getId().equals(id.getValue())),
            "CHT report _id must be preserved as Encounter identifier");
    }

    @Test
    void reportToEncounter_FormTypePreservation() throws Exception {
        CHTReport report = createRealReport();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = reportTransformer.transform(report);
        Encounter encounter = (Encounter) wrappers.get(0).getResource();

        assertTrue(encounter.getType().stream()
            .flatMap(cc -> cc.getCoding().stream())
            .anyMatch(c -> "infant_child".equals(c.getCode())),
            "Form type 'infant_child' should be preserved in Encounter type");
    }

    @Test
    void reportToEncounter_PatientReferencePreservation() throws Exception {
        CHTReport report = createRealReport();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = reportTransformer.transform(report);
        Encounter encounter = (Encounter) wrappers.get(0).getResource();

        assertNotNull(encounter.getSubject());
        assertTrue(encounter.getSubject().getReference()
            .contains("9339db36-040b-4c0c-b2b2-347f5c44983c"),
            "Patient reference should contain patient_id");
    }

    @Test
    void reportToEncounter_PractitionerReferencePreservation() throws Exception {
        CHTReport report = createRealReport();

        List<FHIRResourceWrapper<? extends Resource>> wrappers = reportTransformer.transform(report);
        Encounter encounter = (Encounter) wrappers.get(0).getResource();

        assertTrue(encounter.getParticipant().stream()
            .anyMatch(p -> p.getIndividual() != null
                && p.getIndividual().getReference().contains("f9120a3c-1b55-5d5e-87cc-6b6773d7984a")),
            "Submitting CHW should be preserved as participant");
    }

    // ==================== Validation + Transformation Pipeline ====================

    @Test
    void validContact_PassesValidationAndTransformation() throws Exception {
        CHTContact contact = createRealContact();

        CHTContactValidator.ValidationResult valResult = contactValidator.validate(contact);
        assertTrue(valResult.isValid(), "Real CHT contact should pass validation");

        FHIRResourceWrapper<Patient> wrapper = personTransformer.transform(contact);
        assertNotNull(wrapper);
        assertNotNull(wrapper.getResource());
    }

    @Test
    void validReport_PassesValidationAndTransformation() throws Exception {
        CHTReport report = createRealReport();

        CHTReportValidator.ValidationResult valResult = reportValidator.validate(report);
        assertTrue(valResult.isValid(), "Real CHT report should pass validation");

        List<FHIRResourceWrapper<? extends Resource>> wrappers = reportTransformer.transform(report);
        assertNotNull(wrappers);
        assertFalse(wrappers.isEmpty());
    }

    // ==================== Test Data ====================

    private CHTContact createRealContact() {
        CHTContact contact = new CHTContact();
        contact.setId("3973f2aa-3032-41e5-9418-5c493927b2c2");
        contact.setRev("1-d0b29abb43b7802bfb3ce84185b9f9af");
        contact.setType("person");
        contact.setName("Ahmed Ali Hassan");
        contact.setSex("male");
        contact.setDateOfBirth("1991-01-01");
        contact.setPhone("0777123456");
        contact.setReportedDate(1773666649147L);

        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("88f6dc1f-b4d8-4238-90de-ca274921be9b");
        contact.setParent(parent);

        return contact;
    }

    private CHTPlace createRealPlace() {
        CHTPlace place = new CHTPlace();
        place.setId("88f6dc1f-b4d8-4238-90de-ca274921be9b");
        place.setRev("1-d1c46c8e46e843115ac5a79998e7348c");
        place.setType("clinic");
        place.setName("Ahmed Ali Hassan - 12/56");
        place.setGeolocation("");
        place.setReportedDate(1773666649147L);

        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("41da8c22-6f4e-5d8c-9a6f-52bd10f29b60");
        place.setParent(parent);

        CHTPlace.CHTContactRef contactRef = new CHTPlace.CHTContactRef();
        contactRef.setId("3973f2aa-3032-41e5-9418-5c493927b2c2");
        place.setContact(contactRef);

        return place;
    }

    private CHTReport createRealReport() {
        CHTReport report = new CHTReport();
        report.setId("42762c2b-b082-445f-9cc7-17093e5520a8");
        report.setRev("1-99db299061fa55824a3bc5669dd48b20");
        report.setType("data_record");
        report.setForm("infant_child");
        report.setReportedDate(1774948502253L);

        CHTPlace.CHTContactRef contact = new CHTPlace.CHTContactRef();
        contact.setId("f9120a3c-1b55-5d5e-87cc-6b6773d7984a");
        report.setContact(contact);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("patient_id", "9339db36-040b-4c0c-b2b2-347f5c44983c");
        fields.put("child_name", "Hassan Ali Juma");
        fields.put("weight", "7");
        fields.put("height", "52");
        fields.put("immunization_status", "delayed");
        report.setFields(fields);
        report.setPatientId("9339db36-040b-4c0c-b2b2-347f5c44983c");

        return report;
    }
}
