package com.smartbridge.core.validation;

import com.smartbridge.core.model.ucs.UCSClient;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for UCS Client schema validation using the flat OpenSRP structure.
 * Feature: smart-bridge, Property 1: Schema Validation Completeness
 */
class UCSClientValidatorPropertyTest {

    private final UCSClientValidator validator = new UCSClientValidator();

    /**
     * Property: Valid UCS Client objects should always pass validation.
     */
    @Property(tries = 20)
    void validUCSClientAlwaysPassesValidation(
            @ForAll("validUCSClient") UCSClient client) {

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertTrue(result.isValid(),
            "Valid UCS Client should pass validation. Error: " + result.getErrorMessage());
        assertNull(result.getErrorMessage(),
            "Valid client should have no error message");
    }

    /**
     * Property: UCS Clients missing required baseEntityId should always fail validation.
     */
    @Property(tries = 20)
    void missingBaseEntityIdAlwaysFailsValidation(
            @ForAll("validUCSClient") UCSClient client) {

        client.setBaseEntityId(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(),
            "UCS Client without baseEntityId should fail validation");
        assertNotNull(result.getErrorMessage(),
            "Failed validation should provide error message");
    }

    /**
     * Property: UCS Clients missing identifiers should always fail validation.
     */
    @Property(tries = 20)
    void missingIdentifiersAlwaysFailsValidation(
            @ForAll("validUCSClient") UCSClient client) {

        client.setIdentifiers(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(),
            "UCS Client without identifiers should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    /**
     * Property: UCS Clients missing firstName should always fail validation.
     */
    @Property(tries = 20)
    void missingFirstNameAlwaysFailsValidation(
            @ForAll("validUCSClient") UCSClient client) {

        client.setFirstName(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(),
            "UCS Client without firstName should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    /**
     * Property: UCS Clients missing lastName should always fail validation.
     */
    @Property(tries = 20)
    void missingLastNameAlwaysFailsValidation(
            @ForAll("validUCSClient") UCSClient client) {

        client.setLastName(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(),
            "UCS Client without lastName should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    /**
     * Property: UCS Clients missing gender should always fail validation.
     */
    @Property(tries = 20)
    void missingGenderAlwaysFailsValidation(
            @ForAll("validUCSClient") UCSClient client) {

        client.setGender(null);

        UCSClientValidator.ValidationResult result = validator.validate(client);

        assertFalse(result.isValid(),
            "UCS Client without gender should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    /**
     * Property: Null UCS Client should always fail validation with descriptive error.
     */
    @Property(tries = 10)
    void nullClientAlwaysFailsValidation() {
        UCSClientValidator.ValidationResult result = validator.validate(null);

        assertFalse(result.isValid(),
            "Null UCS Client should fail validation");
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().toLowerCase().contains("null"),
            "Error message should mention null client");
    }

    /**
     * Property: Valid JSON strings should always pass validation and be parseable.
     */
    @Property(tries = 20)
    void validJsonAlwaysPassesValidationAndParsing(
            @ForAll("validUCSClient") UCSClient client) throws Exception {

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        String jsonString = mapper.writeValueAsString(client);

        UCSClientValidator.ValidationResult result = validator.validateJson(jsonString);
        assertTrue(result.isValid(),
            "Valid JSON should pass validation. Error: " + result.getErrorMessage());

        UCSClient parsedClient = validator.parseAndValidate(jsonString);
        assertNotNull(parsedClient, "Parsed client should not be null");
        assertEquals(client.getBaseEntityId(), parsedClient.getBaseEntityId(),
                    "Parsed client should have same baseEntityId");
    }

    /**
     * Property: Empty or null JSON strings should always fail validation.
     */
    @Property(tries = 10)
    void emptyOrNullJsonAlwaysFailsValidation(
            @ForAll("emptyOrNullString") String jsonString) {

        UCSClientValidator.ValidationResult result = validator.validateJson(jsonString);

        assertFalse(result.isValid(),
            "Empty or null JSON should fail validation");
        assertNotNull(result.getErrorMessage());
    }

    // ==================== Arbitraries (Data Generators) ====================

    @Provide
    Arbitrary<UCSClient> validUCSClient() {
        return Combinators.combine(
            validBaseEntityId(),
            validIdentifiers(),
            validFirstName(),
            validLastName(),
            validGender(),
            validBirthdate()
        ).as((baseEntityId, identifiers, firstName, lastName, gender, birthdate) -> {
            UCSClient client = new UCSClient();
            client.setBaseEntityId(baseEntityId);
            client.setIdentifiers(identifiers);
            client.setFirstName(firstName);
            client.setLastName(lastName);
            client.setGender(gender);
            client.setBirthdate(birthdate);
            return client;
        });
    }

    @Provide
    Arbitrary<String> validBaseEntityId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(36);
    }

    @Provide
    Arbitrary<Map<String, String>> validIdentifiers() {
        Arbitrary<String> opensrpId = Arbitraries.strings()
            .withCharRange('A', 'Z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20);

        return opensrpId.map(id -> {
            Map<String, String> map = new HashMap<>();
            map.put("opensrp_id", id);
            return map;
        });
    }

    @Provide
    Arbitrary<String> validFirstName() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> validLastName() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> validGender() {
        return Arbitraries.of("Male", "Female", "M", "F", "Other");
    }

    @Provide
    Arbitrary<String> validBirthdate() {
        return Arbitraries.longs()
            .between(
                java.time.LocalDate.of(1900, 1, 1).toEpochDay(),
                java.time.LocalDate.now().toEpochDay()
            )
            .map(epochDay -> java.time.LocalDate.ofEpochDay(epochDay).toString());
    }

    @Provide
    Arbitrary<String> emptyOrNullString() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
}
