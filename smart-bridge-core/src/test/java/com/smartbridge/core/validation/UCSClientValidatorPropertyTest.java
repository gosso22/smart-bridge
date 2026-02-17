package com.smartbridge.core.validation;

import com.smartbridge.core.model.ucs.UCSClient;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for UCS Client schema validation.
 * Feature: smart-bridge, Property 1: Schema Validation Completeness
 * 
 * Tests that for any input data from legacy systems, the system correctly validates
 * the data against the appropriate schema and rejects invalid inputs with descriptive error messages.
 * 
 * Validates: Requirements 1.1, 1.2, 6.1, 6.5
 */
class UCSClientValidatorPropertyTest {

    private final UCSClientValidator validator = new UCSClientValidator();

    /**
     * Property: Valid UCS Client objects should always pass validation.
     * For any valid UCS Client with all required fields, validation should succeed.
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
     * Property: UCS Clients missing required identifiers should always fail validation.
     * For any UCS Client without opensrp_id, validation should fail with descriptive error.
     */
    @Property(tries = 20)
    void missingRequiredIdentifierAlwaysFailsValidation(
            @ForAll("validDemographics") UCSClient.UCSDemographics demographics,
            @ForAll("validMetadata") UCSClient.UCSMetadata metadata) {
        
        // Create client with null identifiers
        UCSClient client = new UCSClient(null, demographics, null, metadata);
        
        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertFalse(result.isValid(), 
            "UCS Client without identifiers should fail validation");
        assertNotNull(result.getErrorMessage(), 
            "Failed validation should provide error message");
        assertTrue(result.getErrorMessage().toLowerCase().contains("identifier") ||
                   result.getErrorMessage().toLowerCase().contains("required"),
            "Error message should mention missing identifiers or required fields");
    }

    /**
     * Property: UCS Clients with invalid gender codes should always fail validation.
     * For any gender value not in {M, F, O}, validation should fail.
     */
    @Property(tries = 20)
    void invalidGenderAlwaysFailsValidation(
            @ForAll("validIdentifiers") UCSClient.UCSIdentifiers identifiers,
            @ForAll("invalidGender") String invalidGender,
            @ForAll @NotBlank @StringLength(min = 1, max = 50) String firstName,
            @ForAll @NotBlank @StringLength(min = 1, max = 50) String lastName,
            @ForAll("validBirthDate") LocalDate birthDate,
            @ForAll("validMetadata") UCSClient.UCSMetadata metadata) {
        
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics(
            firstName, lastName, invalidGender, birthDate, null
        );
        
        UCSClient client = new UCSClient(identifiers, demographics, null, metadata);
        
        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertFalse(result.isValid(), 
            "UCS Client with invalid gender '" + invalidGender + "' should fail validation");
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().toLowerCase().contains("gender") ||
                   result.getErrorMessage().toLowerCase().contains("enum"),
            "Error message should mention gender validation issue");
    }

    /**
     * Property: UCS Clients missing required demographic fields should always fail validation.
     * For any UCS Client without required demographics, validation should fail.
     */
    @Property(tries = 20)
    void missingRequiredDemographicsAlwaysFailsValidation(
            @ForAll("validIdentifiers") UCSClient.UCSIdentifiers identifiers,
            @ForAll("validMetadata") UCSClient.UCSMetadata metadata) {
        
        // Create client with null demographics
        UCSClient client = new UCSClient(identifiers, null, null, metadata);
        
        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertFalse(result.isValid(), 
            "UCS Client without demographics should fail validation");
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().toLowerCase().contains("demographic") ||
                   result.getErrorMessage().toLowerCase().contains("required"),
            "Error message should mention missing demographics");
    }

    /**
     * Property: UCS Clients missing required metadata should always fail validation.
     * For any UCS Client without source in metadata, validation should fail.
     */
    @Property(tries = 20)
    void missingRequiredMetadataAlwaysFailsValidation(
            @ForAll("validIdentifiers") UCSClient.UCSIdentifiers identifiers,
            @ForAll("validDemographics") UCSClient.UCSDemographics demographics) {
        
        // Create client with null metadata
        UCSClient client = new UCSClient(identifiers, demographics, null, null);
        
        UCSClientValidator.ValidationResult result = validator.validate(client);
        
        assertFalse(result.isValid(), 
            "UCS Client without metadata should fail validation");
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
     * For any valid UCS Client JSON, validation and parsing should succeed.
     */
    @Property(tries = 20)
    void validJsonAlwaysPassesValidationAndParsing(
            @ForAll("validUCSClient") UCSClient client) throws Exception {
        
        // Convert client to JSON string with proper date serialization
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String jsonString = mapper.writeValueAsString(client);
        
        // Validate JSON
        UCSClientValidator.ValidationResult result = validator.validateJson(jsonString);
        assertTrue(result.isValid(), 
            "Valid JSON should pass validation. Error: " + result.getErrorMessage());
        
        // Parse and validate
        UCSClient parsedClient = validator.parseAndValidate(jsonString);
        assertNotNull(parsedClient, "Parsed client should not be null");
        assertEquals(client.getIdentifiers().getOpensrpId(), 
                    parsedClient.getIdentifiers().getOpensrpId(),
                    "Parsed client should have same opensrp_id");
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
            validIdentifiers(),
            validDemographics(),
            validClinicalData(),
            validMetadata()
        ).as(UCSClient::new);
    }

    @Provide
    Arbitrary<UCSClient.UCSIdentifiers> validIdentifiers() {
        Arbitrary<String> opensrpId = Arbitraries.strings()
            .withCharRange('A', 'Z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20);
        
        Arbitrary<String> nationalId = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20)
            .injectNull(0.3); // 30% chance of null
        
        return Combinators.combine(opensrpId, nationalId)
            .as(UCSClient.UCSIdentifiers::new);
    }

    @Provide
    Arbitrary<UCSClient.UCSDemographics> validDemographics() {
        Arbitrary<String> firstName = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50);
        
        Arbitrary<String> lastName = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50);
        
        Arbitrary<String> gender = Arbitraries.of("M", "F", "O");
        
        Arbitrary<LocalDate> birthDate = validBirthDate();
        
        Arbitrary<UCSClient.UCSAddress> address = validAddress().injectNull(0.3);
        
        return Combinators.combine(firstName, lastName, gender, birthDate, address)
            .as(UCSClient.UCSDemographics::new);
    }

    @Provide
    Arbitrary<LocalDate> validBirthDate() {
        return Arbitraries.longs()
            .between(LocalDate.of(1900, 1, 1).toEpochDay(), LocalDate.now().toEpochDay())
            .map(LocalDate::ofEpochDay);
    }

    @Provide
    Arbitrary<UCSClient.UCSAddress> validAddress() {
        Arbitrary<String> district = Arbitraries.strings()
            .alpha()
            .withChars(' ')
            .ofMinLength(3)
            .ofMaxLength(50);
        
        Arbitrary<String> ward = Arbitraries.strings()
            .alpha()
            .withChars(' ')
            .ofMinLength(3)
            .ofMaxLength(50);
        
        Arbitrary<String> village = Arbitraries.strings()
            .alpha()
            .withChars(' ')
            .ofMinLength(3)
            .ofMaxLength(50);
        
        return Combinators.combine(district, ward, village)
            .as(UCSClient.UCSAddress::new);
    }

    @Provide
    Arbitrary<UCSClient.UCSClinicalData> validClinicalData() {
        return Arbitraries.just(new UCSClient.UCSClinicalData(
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
        ));
    }

    @Provide
    Arbitrary<UCSClient.UCSMetadata> validMetadata() {
        Arbitrary<LocalDateTime> createdAt = Arbitraries.longs()
            .between(
                LocalDateTime.of(2020, 1, 1, 0, 0).toEpochSecond(java.time.ZoneOffset.UTC),
                LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC)
            )
            .map(epochSecond -> LocalDateTime.ofEpochSecond(epochSecond, 0, java.time.ZoneOffset.UTC))
            .injectNull(0.2);
        
        Arbitrary<LocalDateTime> updatedAt = Arbitraries.longs()
            .between(
                LocalDateTime.of(2020, 1, 1, 0, 0).toEpochSecond(java.time.ZoneOffset.UTC),
                LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC)
            )
            .map(epochSecond -> LocalDateTime.ofEpochSecond(epochSecond, 0, java.time.ZoneOffset.UTC))
            .injectNull(0.2);
        
        Arbitrary<String> source = Arbitraries.of("UCS", "GoTHOMIS", "LEGACY");
        
        Arbitrary<String> fhirId = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-')
            .ofMinLength(10)
            .ofMaxLength(30)
            .injectNull(0.5);
        
        return Combinators.combine(createdAt, updatedAt, source, fhirId)
            .as(UCSClient.UCSMetadata::new);
    }

    @Provide
    Arbitrary<String> invalidGender() {
        // Generate any string that is NOT M, F, or O
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(10)
            .filter(s -> !s.equals("M") && !s.equals("F") && !s.equals("O"));
    }

    @Provide
    Arbitrary<String> emptyOrNullString() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
}
