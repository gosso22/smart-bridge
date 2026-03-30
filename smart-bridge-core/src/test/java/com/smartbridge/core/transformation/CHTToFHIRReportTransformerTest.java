package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CHTToFHIRReportTransformerTest {

    private CHTToFHIRReportTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CHTToFHIRReportTransformer();
    }

    @Test
    void testTransform_ValidReport_WithFields() throws TransformationException {
        CHTReport report = createValidReport();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("weight", 65.5);
        fields.put("temperature", 36.8);
        report.setFields(fields);

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        // 1 Encounter + 2 Observations
        assertEquals(3, result.size());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.ENCOUNTER, result.get(0).getResourceType());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.OBSERVATION, result.get(1).getResourceType());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.OBSERVATION, result.get(2).getResourceType());
    }

    @Test
    void testTransform_ValidReport_NoFields() throws TransformationException {
        CHTReport report = createValidReport();
        report.setFields(null);

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        // Encounter only
        assertEquals(1, result.size());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.ENCOUNTER, result.get(0).getResourceType());
    }

    @Test
    void testTransform_ValidReport_EmptyFields() throws TransformationException {
        CHTReport report = createValidReport();
        report.setFields(Map.of());

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        assertEquals(1, result.size());
    }

    @Test
    void testTransform_EncounterIdentifier() throws TransformationException {
        CHTReport report = createValidReport();

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertEquals(1, encounter.getIdentifier().size());
        assertEquals(CHTToFHIRReportTransformer.CHT_REPORT_UUID_SYSTEM,
            encounter.getIdentifier().get(0).getSystem());
        assertEquals("cht-report-001", encounter.getIdentifier().get(0).getValue());
    }

    @Test
    void testTransform_EncounterStatus() throws TransformationException {
        CHTReport report = createValidReport();

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertEquals(Encounter.EncounterStatus.FINISHED, encounter.getStatus());
    }

    @Test
    void testTransform_EncounterType() throws TransformationException {
        CHTReport report = createValidReport();

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertEquals(1, encounter.getType().size());
        assertEquals("pregnancy_visit", encounter.getType().get(0).getCodingFirstRep().getCode());
        assertEquals(CHTToFHIRReportTransformer.CHT_FORM_TYPE_SYSTEM,
            encounter.getType().get(0).getCodingFirstRep().getSystem());
    }

    @Test
    void testTransform_EncounterPeriod() throws TransformationException {
        CHTReport report = createValidReport();
        report.setReportedDate(1680100000000L);

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertNotNull(encounter.getPeriod());
        assertNotNull(encounter.getPeriod().getStart());
        assertEquals(1680100000000L, encounter.getPeriod().getStart().getTime());
    }

    @Test
    void testTransform_EncounterSubject_PatientUuid() throws TransformationException {
        CHTReport report = createValidReport();
        report.setPatientUuid("cht-person-005");
        report.setPatientId("12345");

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        // patient_uuid takes priority over patient_id
        assertEquals("Patient/cht-person-005", encounter.getSubject().getReference());
    }

    @Test
    void testTransform_EncounterSubject_PatientId_Fallback() throws TransformationException {
        CHTReport report = createValidReport();
        report.setPatientUuid(null);
        report.setPatientId("12345");

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertEquals("Patient/12345", encounter.getSubject().getReference());
    }

    @Test
    void testTransform_EncounterSubject_NoPatient() throws TransformationException {
        CHTReport report = createValidReport();
        report.setPatientUuid(null);
        report.setPatientId(null);

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertFalse(encounter.hasSubject());
    }

    @Test
    void testTransform_EncounterParticipant() throws TransformationException {
        CHTReport report = createValidReport();
        CHTPlace.CHTContactRef contactRef = new CHTPlace.CHTContactRef();
        contactRef.setId("chw-001");
        contactRef.setName("CHW Amina");
        report.setContact(contactRef);

        Encounter encounter = (Encounter) transformer.transform(report).get(0).getResource();

        assertEquals(1, encounter.getParticipant().size());
        assertEquals("Practitioner/chw-001",
            encounter.getParticipant().get(0).getIndividual().getReference());
    }

    @Test
    void testTransform_ObservationWeight() throws TransformationException {
        CHTReport report = createValidReport();
        report.setFields(Map.of("weight", 65.5));

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        assertEquals(2, result.size());
        Observation obs = (Observation) result.get(1).getResource();

        assertEquals("http://loinc.org", obs.getCode().getCodingFirstRep().getSystem());
        assertEquals("29463-7", obs.getCode().getCodingFirstRep().getCode());
        assertTrue(obs.getValue() instanceof Quantity);
        assertEquals(65.5, ((Quantity) obs.getValue()).getValue().doubleValue(), 0.001);
        assertEquals("kg", ((Quantity) obs.getValue()).getUnit());
    }

    @Test
    void testTransform_ObservationTemperature() throws TransformationException {
        CHTReport report = createValidReport();
        report.setFields(Map.of("temperature", 36.8));

        Observation obs = (Observation) transformer.transform(report).get(1).getResource();

        assertEquals("8310-5", obs.getCode().getCodingFirstRep().getCode());
        assertTrue(obs.getValue() instanceof Quantity);
        assertEquals(36.8, ((Quantity) obs.getValue()).getValue().doubleValue(), 0.001);
    }

    @Test
    void testTransform_ObservationUnknownField() throws TransformationException {
        CHTReport report = createValidReport();
        report.setFields(Map.of("custom_field", "some value"));

        Observation obs = (Observation) transformer.transform(report).get(1).getResource();

        assertEquals(CHTToFHIRReportTransformer.CHT_OBSERVATION_SYSTEM,
            obs.getCode().getCodingFirstRep().getSystem());
        assertEquals("custom_field", obs.getCode().getCodingFirstRep().getCode());
        assertTrue(obs.getValue() instanceof StringType);
        assertEquals("some value", ((StringType) obs.getValue()).getValue());
    }

    @Test
    void testTransform_ObservationSubject() throws TransformationException {
        CHTReport report = createValidReport();
        report.setPatientUuid("cht-person-005");
        report.setFields(Map.of("weight", 65.5));

        Observation obs = (Observation) transformer.transform(report).get(1).getResource();

        assertEquals("Patient/cht-person-005", obs.getSubject().getReference());
    }

    @Test
    void testTransform_ObservationEffectiveDate() throws TransformationException {
        CHTReport report = createValidReport();
        report.setReportedDate(1680100000000L);
        report.setFields(Map.of("weight", 65.5));

        Observation obs = (Observation) transformer.transform(report).get(1).getResource();

        assertTrue(obs.getEffective() instanceof DateTimeType);
    }

    @Test
    void testTransform_MetadataFieldsSkipped() throws TransformationException {
        CHTReport report = createValidReport();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("weight", 65.5);
        fields.put("patient_id", "12345");
        fields.put("patient_name", "Amina Juma");
        fields.put("inputs", "{}");
        fields.put("meta_instanceID", "uuid:abc");
        fields.put("source", "contact");
        report.setFields(fields);

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        // Only 1 Encounter + 1 Observation (weight); metadata fields should be skipped
        assertEquals(2, result.size());
    }

    @Test
    void testTransform_NullFieldValueSkipped() throws TransformationException {
        CHTReport report = createValidReport();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("weight", 65.5);
        fields.put("blood_pressure_systolic", null);
        report.setFields(fields);

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        // Null field value should be skipped
        assertEquals(2, result.size());
    }

    @Test
    void testTransform_NullReport() {
        assertThrows(TransformationException.class, () -> transformer.transform(null));
    }

    @Test
    void testTransform_MissingId() {
        CHTReport report = createValidReport();
        report.setId(null);

        assertThrows(TransformationException.class, () -> transformer.transform(report));
    }

    @Test
    void testTransform_MissingForm() {
        CHTReport report = createValidReport();
        report.setForm(null);

        TransformationException ex = assertThrows(TransformationException.class,
            () -> transformer.transform(report));
        assertTrue(ex.getMessage().contains("form"));
    }

    @Test
    void testTransform_MissingReportedDate() {
        CHTReport report = createValidReport();
        report.setReportedDate(null);

        assertThrows(TransformationException.class, () -> transformer.transform(report));
    }

    @Test
    void testTransform_SourceSystem() throws TransformationException {
        CHTReport report = createValidReport();

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        assertEquals("CHT", result.get(0).getSourceSystem());
        assertEquals("cht-report-001", result.get(0).getOriginalId());
    }

    @Test
    void testTransform_NumericStringField() throws TransformationException {
        CHTReport report = createValidReport();
        report.setFields(Map.of("weight", "65.5"));

        Observation obs = (Observation) transformer.transform(report).get(1).getResource();

        // String "65.5" should be parsed as numeric for known clinical field
        assertTrue(obs.getValue() instanceof Quantity);
        assertEquals(65.5, ((Quantity) obs.getValue()).getValue().doubleValue(), 0.001);
    }

    // Helper
    private CHTReport createValidReport() {
        CHTReport report = new CHTReport();
        report.setId("cht-report-001");
        report.setRev("1-ghi789");
        report.setType("data_record");
        report.setForm("pregnancy_visit");
        report.setPatientUuid("cht-person-005");
        report.setReportedDate(1680100000000L);
        return report;
    }
}
