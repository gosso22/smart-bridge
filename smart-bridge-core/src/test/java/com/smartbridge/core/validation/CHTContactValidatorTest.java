package com.smartbridge.core.validation;

import com.smartbridge.core.model.cht.CHTContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CHT Contact validation against the CHT contact schema.
 */
class CHTContactValidatorTest {

    private CHTContactValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CHTContactValidator();
    }

    @Test
    void testValidCHTContact() {
        CHTContact contact = createValidContact();

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertTrue(result.isValid(), "Valid CHT Contact should pass validation: " + result.getErrorMessage());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTContact_MissingId() {
        CHTContact contact = createValidContact();
        contact.setId(null);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(), "CHT Contact without _id should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTContact_EmptyId() {
        CHTContact contact = createValidContact();
        contact.setId("");

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(), "CHT Contact with empty _id should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTContact_MissingName() {
        CHTContact contact = createValidContact();
        contact.setName(null);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(), "CHT Contact without name should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTContact_EmptyName() {
        CHTContact contact = createValidContact();
        contact.setName("");

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(), "CHT Contact with empty name should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidCHTContact_MissingType() {
        CHTContact contact = createValidContact();
        contact.setType(null);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(), "CHT Contact without type should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testValidCHTContact_MinimalFields() {
        CHTContact contact = new CHTContact();
        contact.setId("min-001");
        contact.setName("Test Person");
        contact.setType("contact");

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertTrue(result.isValid(), "Minimal CHT Contact should pass: " + result.getErrorMessage());
    }

    @Test
    void testValidateNull() {
        CHTContactValidator.ValidationResult result = validator.validate(null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null"));
    }

    @Test
    void testValidateJson_ValidJson() {
        String validJson = """
            {
              "_id": "cht-person-001",
              "_rev": "1-abc123",
              "type": "contact",
              "contact_type": "person",
              "name": "Amina Juma",
              "date_of_birth": "1985-06-15",
              "sex": "female",
              "phone": "+255712345678",
              "patient_id": "12345",
              "reported_date": 1680000000000
            }
            """;

        CHTContactValidator.ValidationResult result = validator.validateJson(validJson);

        assertTrue(result.isValid(), "Valid JSON should pass validation: " + result.getErrorMessage());
    }

    @Test
    void testValidateJson_InvalidJson_MissingRequired() {
        String invalidJson = """
            {
              "_id": "cht-person-001",
              "contact_type": "person",
              "sex": "female"
            }
            """;

        CHTContactValidator.ValidationResult result = validator.validateJson(invalidJson);

        assertFalse(result.isValid(), "JSON missing required fields should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testValidateJson_NullJson() {
        CHTContactValidator.ValidationResult result = validator.validateJson(null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null or empty"));
    }

    @Test
    void testValidateJson_EmptyJson() {
        CHTContactValidator.ValidationResult result = validator.validateJson("");

        assertFalse(result.isValid());
    }

    @Test
    void testParseAndValidate_Success() throws ValidationException {
        String validJson = """
            {
              "_id": "cht-person-002",
              "type": "contact",
              "contact_type": "person",
              "name": "Hassan Mwinyi",
              "date_of_birth": "1990-03-20",
              "sex": "male",
              "patient_id": "67890"
            }
            """;

        CHTContact contact = validator.parseAndValidate(validJson);

        assertNotNull(contact);
        assertEquals("cht-person-002", contact.getId());
        assertEquals("contact", contact.getType());
        assertEquals("Hassan Mwinyi", contact.getName());
        assertEquals("male", contact.getSex());
        assertEquals("67890", contact.getPatientId());
    }

    @Test
    void testParseAndValidate_Failure() {
        String invalidJson = """
            {
              "_id": "cht-person-003",
              "contact_type": "person"
            }
            """;

        assertThrows(ValidationException.class, () -> validator.parseAndValidate(invalidJson));
    }

    @Test
    void testValidCHTContact_WithParent() {
        CHTContact contact = createValidContact();
        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("cht-clinic-001");
        parent.setName("Kariakoo Clinic");
        parent.setContactType("clinic");
        contact.setParent(parent);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertTrue(result.isValid(), "CHT Contact with parent should pass: " + result.getErrorMessage());
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
