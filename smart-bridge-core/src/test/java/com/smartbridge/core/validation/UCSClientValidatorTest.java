package com.smartbridge.core.validation;

import com.smartbridge.core.model.ucs.UCSClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UCS Client validation.
 */
class UCSClientValidatorTest {

    private UCSClientValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UCSClientValidator();
    }

    @Test
    void testValidUCSClient() {
        // Create a valid UCS Client
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers("OSR-12345", "NID-67890");
        
        UCSClient.UCSAddress address = new UCSClient.UCSAddress("Dar es Salaam", "Kinondoni", "Mwenge");
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics(
            "John", "Doe", "M", LocalDate.of(1990, 5, 15), address
        );
        
        UCSClient.UCSClinicalData clinicalData = new UCSClient.UCSClinicalData(
            new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>()
        );
        
        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata(
            LocalDateTime.now(), LocalDateTime.now(), "UCS", null
        );
        
        UCSClient client = new UCSClient(identifiers, demographics, clinicalData, metadata);

        // Validate
        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertTrue(result.isValid(), "Valid UCS Client should pass validation");
        assertNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_MissingIdentifiers() {
        // Create UCS Client with null identifiers
        UCSClient client = new UCSClient();
        client.setDemographics(new UCSClient.UCSDemographics(
            "John", "Doe", "M", LocalDate.of(1990, 5, 15), null
        ));
        client.setMetadata(new UCSClient.UCSMetadata(
            LocalDateTime.now(), LocalDateTime.now(), "UCS", null
        ));

        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertFalse(result.isValid(), "UCS Client without identifiers should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testInvalidUCSClient_InvalidGender() {
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers("OSR-12345", null);
        
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics(
            "John", "Doe", "X", LocalDate.of(1990, 5, 15), null
        );
        
        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata(
            LocalDateTime.now(), LocalDateTime.now(), "UCS", null
        );
        
        UCSClient client = new UCSClient(identifiers, demographics, null, metadata);

        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertFalse(result.isValid(), "UCS Client with invalid gender should fail validation");
        assertTrue(result.getErrorMessage().contains("gender"));
    }

    @Test
    void testValidateJson_ValidJson() {
        String validJson = """
            {
              "identifiers": {
                "opensrp_id": "OSR-12345",
                "national_id": "NID-67890"
              },
              "demographics": {
                "firstName": "John",
                "lastName": "Doe",
                "gender": "M",
                "birthDate": "1990-05-15",
                "address": {
                  "district": "Dar es Salaam",
                  "ward": "Kinondoni",
                  "village": "Mwenge"
                }
              },
              "clinicalData": {
                "observations": [],
                "medications": [],
                "procedures": []
              },
              "metadata": {
                "createdAt": "2024-01-15T10:30:00",
                "updatedAt": "2024-01-15T10:30:00",
                "source": "UCS"
              }
            }
            """;

        UCSClientValidator.ValidationResult result = validator.validateJson(validJson);
        
        assertTrue(result.isValid(), "Valid JSON should pass validation");
    }

    @Test
    void testValidateJson_InvalidJson() {
        String invalidJson = """
            {
              "identifiers": {
                "national_id": "NID-67890"
              },
              "demographics": {
                "firstName": "John",
                "gender": "M",
                "birthDate": "1990-05-15"
              }
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
              "identifiers": {
                "opensrp_id": "OSR-12345"
              },
              "demographics": {
                "firstName": "Jane",
                "lastName": "Smith",
                "gender": "F",
                "birthDate": "1985-03-20"
              },
              "metadata": {
                "source": "UCS"
              }
            }
            """;

        UCSClient client = validator.parseAndValidate(validJson);
        
        assertNotNull(client);
        assertEquals("OSR-12345", client.getIdentifiers().getOpensrpId());
        assertEquals("Jane", client.getDemographics().getFirstName());
        assertEquals("F", client.getDemographics().getGender());
    }

    @Test
    void testParseAndValidate_Failure() {
        String invalidJson = """
            {
              "identifiers": {},
              "demographics": {
                "firstName": "Jane"
              }
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
}
