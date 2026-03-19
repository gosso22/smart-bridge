package com.smartbridge.core.validation;

import com.smartbridge.core.model.ucs.UCSClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UCS Client validation against the OpenSRP schema.
 */
class UCSClientValidatorTest {

    private UCSClientValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UCSClientValidator();
    }

    @Test
    void testValidUCSClient() {
        UCSClient client = createValidClient();

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertTrue(result.isValid(), "Valid UCS Client should pass validation: " + result.getErrorMessage());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_MissingBaseEntityId() {
        UCSClient client = createValidClient();
        client.setBaseEntityId(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(), "UCS Client without baseEntityId should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_MissingFirstName() {
        UCSClient client = createValidClient();
        client.setFirstName(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(), "UCS Client without firstName should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_MissingLastName() {
        UCSClient client = createValidClient();
        client.setLastName(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(), "UCS Client without lastName should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_MissingGender() {
        UCSClient client = createValidClient();
        client.setGender(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(), "UCS Client without gender should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_MissingIdentifiers() {
        UCSClient client = createValidClient();
        client.setIdentifiers(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(), "UCS Client without identifiers should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testValidateJson_ValidJson() {
        String validJson = """
            {
              "baseEntityId": "test-001",
              "identifiers": {
                "opensrp_id": "OSR-12345"
              },
              "firstName": "John",
              "lastName": "Doe",
              "gender": "Male",
              "birthdate": "1990-05-15T00:00:00.000+0300",
              "addresses": [
                {
                  "addressType": "usual_residence",
                  "country": "Tanzania",
                  "countyDistrict": "Ilala",
                  "cityVillage": "Kariakoo"
                }
              ],
              "attributes": {
                "national_id": "NID-67890"
              }
            }
            """;

        UCSClientValidator.ValidationResult result = validator.validateJson(validJson);

        assertTrue(result.isValid(), "Valid JSON should pass validation: " + result.getErrorMessage());
    }

    @Test
    void testValidateJson_InvalidJson_MissingRequired() {
        String invalidJson = """
            {
              "identifiers": {
                "opensrp_id": "OSR-12345"
              },
              "firstName": "John",
              "gender": "Male"
            }
            """;

        UCSClientValidator.ValidationResult result = validator.validateJson(invalidJson);

        assertFalse(result.isValid(), "Invalid JSON should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testParseAndValidate_Success() throws ValidationException {
        String validJson = """
            {
              "baseEntityId": "test-001",
              "identifiers": {
                "opensrp_id": "OSR-12345"
              },
              "firstName": "Jane",
              "lastName": "Smith",
              "gender": "Female",
              "birthdate": "1985-03-20"
            }
            """;

        UCSClient client = validator.parseAndValidate(validJson);

        assertNotNull(client);
        assertEquals("test-001", client.getBaseEntityId());
        assertEquals("OSR-12345", client.getOpensrpId());
        assertEquals("Jane", client.getFirstName());
        assertEquals("Female", client.getGender());
    }

    @Test
    void testParseAndValidate_Failure() {
        String invalidJson = """
            {
              "identifiers": {},
              "firstName": "Jane"
            }
            """;

        assertThrows(ValidationException.class, () -> validator.parseAndValidate(invalidJson));
    }

    @Test
    void testValidateNull() {
        UCSClientValidator.ValidationResult result = validator.validate(null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null"));
    }

    // Helper
    private UCSClient createValidClient() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("test-entity-001");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "OSR-12345");
        client.setIdentifiers(identifiers);

        client.setFirstName("John");
        client.setLastName("Doe");
        client.setGender("Male");
        client.setBirthdate("1990-05-15");

        return client;
    }
}
