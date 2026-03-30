package com.smartbridge.core.validation;

import com.smartbridge.core.model.cht.CHTReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CHT Report validation against the CHT report schema.
 */
class CHTReportValidatorTest {

    private CHTReportValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CHTReportValidator();
    }

    @Test
    void testValidCHTReport() {
        CHTReport report = createValidReport();

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertTrue(result.isValid(), "Valid CHT Report should pass validation: " + result.getErrorMessage());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTReport_MissingId() {
        CHTReport report = createValidReport();
        report.setId(null);

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertFalse(result.isValid(), "CHT Report without _id should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTReport_MissingType() {
        CHTReport report = createValidReport();
        report.setType(null);

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertFalse(result.isValid(), "CHT Report without type should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTReport_WrongType() {
        CHTReport report = createValidReport();
        report.setType("contact");

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertFalse(result.isValid(), "CHT Report with type != data_record should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTReport_MissingForm() {
        CHTReport report = createValidReport();
        report.setForm(null);

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertFalse(result.isValid(), "CHT Report without form should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTReport_EmptyForm() {
        CHTReport report = createValidReport();
        report.setForm("");

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertFalse(result.isValid(), "CHT Report with empty form should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTReport_MissingReportedDate() {
        CHTReport report = createValidReport();
        report.setReportedDate(null);

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertFalse(result.isValid(), "CHT Report without reported_date should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testValidCHTReport_MinimalFields() {
        CHTReport report = new CHTReport();
        report.setId("rpt-001");
        report.setType("data_record");
        report.setForm("pregnancy_visit");
        report.setReportedDate(1680000000000L);

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertTrue(result.isValid(), "Minimal CHT Report should pass: " + result.getErrorMessage());
    }

    @Test
    void testValidateNull() {
        CHTReportValidator.ValidationResult result = validator.validate(null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null"));
    }

    @Test
    void testValidateJson_ValidJson() {
        String validJson = """
            {
              "_id": "cht-report-001",
              "_rev": "1-ghi789",
              "type": "data_record",
              "form": "pregnancy_visit",
              "fields": {
                "weight": 65.5,
                "temperature": 36.8
              },
              "patient_id": "12345",
              "patient_uuid": "cht-person-001",
              "reported_date": 1680100000000
            }
            """;

        CHTReportValidator.ValidationResult result = validator.validateJson(validJson);

        assertTrue(result.isValid(), "Valid JSON should pass validation: " + result.getErrorMessage());
    }

    @Test
    void testValidateJson_InvalidJson_MissingRequired() {
        String invalidJson = """
            {
              "_id": "cht-report-002",
              "type": "data_record"
            }
            """;

        CHTReportValidator.ValidationResult result = validator.validateJson(invalidJson);

        assertFalse(result.isValid(), "JSON missing required fields should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testValidateJson_NullJson() {
        CHTReportValidator.ValidationResult result = validator.validateJson(null);

        assertFalse(result.isValid());
    }

    @Test
    void testParseAndValidate_Success() throws ValidationException {
        String validJson = """
            {
              "_id": "cht-report-003",
              "type": "data_record",
              "form": "immunization",
              "reported_date": 1680200000000,
              "patient_uuid": "cht-person-005"
            }
            """;

        CHTReport report = validator.parseAndValidate(validJson);

        assertNotNull(report);
        assertEquals("cht-report-003", report.getId());
        assertEquals("data_record", report.getType());
        assertEquals("immunization", report.getForm());
        assertEquals(1680200000000L, report.getReportedDate());
        assertEquals("cht-person-005", report.getPatientUuid());
    }

    @Test
    void testParseAndValidate_Failure() {
        String invalidJson = """
            {
              "_id": "cht-report-004",
              "type": "data_record"
            }
            """;

        assertThrows(ValidationException.class, () -> validator.parseAndValidate(invalidJson));
    }

    @Test
    void testValidCHTReport_WithFields() {
        CHTReport report = createValidReport();
        report.setFields(Map.of(
            "weight", 65.5,
            "blood_pressure_systolic", 120,
            "temperature", 36.8
        ));

        CHTReportValidator.ValidationResult result = validator.validate(report);

        assertTrue(result.isValid(), "CHT Report with fields should pass: " + result.getErrorMessage());
    }

    // Helper
    private CHTReport createValidReport() {
        CHTReport report = new CHTReport();
        report.setId("cht-report-001");
        report.setRev("1-ghi789");
        report.setType("data_record");
        report.setForm("pregnancy_visit");
        report.setPatientId("12345");
        report.setPatientUuid("cht-person-001");
        report.setReportedDate(1680100000000L);
        return report;
    }
}
