package com.smartbridge.core.validation;

import com.smartbridge.core.model.cht.CHTContact;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CHT Contact validation completeness.
 * Feature: cht-integration, Property 1: CHT Contact Validation Completeness
 *
 * Validates: Requirements 1.8, 1.9, 10.1, 10.3
 */
class CHTContactValidatorPropertyTest {

    private final CHTContactValidator validator = new CHTContactValidator();

    // ==================== Valid Contacts Always Pass ====================

    @Property(tries = 100)
    void validContactsAlwaysPassValidation(@ForAll("validContact") CHTContact contact) {
        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertTrue(result.isValid(),
            "Valid contact should always pass validation, but got: " + result.getErrorMessage());
    }

    @Property(tries = 100)
    void validContactValidationNeverReturnsNullResult(@ForAll("validContact") CHTContact contact) {
        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertNotNull(result, "Validation result must never be null");
    }

    // ==================== Missing Required Fields Always Fail ====================

    @Property(tries = 50)
    void contactMissingIdAlwaysFails(
            @ForAll("validName") String name,
            @ForAll("validType") String type) {
        CHTContact contact = new CHTContact();
        contact.setName(name);
        contact.setType(type);
        // _id is null

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(),
            "Contact without _id should always fail validation");
        assertNotNull(result.getErrorMessage());
    }

    @Property(tries = 50)
    void contactMissingNameAlwaysFails(
            @ForAll("validId") String id,
            @ForAll("validType") String type) {
        CHTContact contact = new CHTContact();
        contact.setId(id);
        contact.setType(type);
        // name is null

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(),
            "Contact without name should always fail validation");
    }

    @Property(tries = 50)
    void contactMissingTypeAlwaysFails(
            @ForAll("validId") String id,
            @ForAll("validName") String name) {
        CHTContact contact = new CHTContact();
        contact.setId(id);
        contact.setName(name);
        // type is null

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(),
            "Contact without type should always fail validation");
    }

    // ==================== Null Always Fails ====================

    @Property(tries = 10)
    void nullContactAlwaysFails() {
        CHTContactValidator.ValidationResult result = validator.validate(null);

        assertFalse(result.isValid(), "Null contact should always fail validation");
        assertNotNull(result.getErrorMessage());
    }

    // ==================== Empty Required Fields Always Fail ====================

    @Property(tries = 50)
    void contactWithEmptyIdAlwaysFails(
            @ForAll("validName") String name,
            @ForAll("validType") String type) {
        CHTContact contact = new CHTContact();
        contact.setId(""); // empty string
        contact.setName(name);
        contact.setType(type);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(),
            "Contact with empty _id should always fail validation");
    }

    @Property(tries = 50)
    void contactWithEmptyNameAlwaysFails(
            @ForAll("validId") String id,
            @ForAll("validType") String type) {
        CHTContact contact = new CHTContact();
        contact.setId(id);
        contact.setName(""); // empty string
        contact.setType(type);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertFalse(result.isValid(),
            "Contact with empty name should always fail validation");
    }

    // ==================== Optional Fields Don't Affect Validity ====================

    @Property(tries = 50)
    void optionalFieldsDoNotAffectValidation(
            @ForAll("validContact") CHTContact contact,
            @ForAll("optionalSex") String sex,
            @ForAll("optionalPhone") String phone) {
        contact.setSex(sex);
        contact.setPhone(phone);

        CHTContactValidator.ValidationResult result = validator.validate(contact);

        assertTrue(result.isValid(),
            "Optional fields should not cause validation failure: " + result.getErrorMessage());
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<CHTContact> validContact() {
        return Combinators.combine(
            validId(),
            validName(),
            validType()
        ).as((id, name, type) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setType(type);
            return c;
        });
    }

    @Provide
    Arbitrary<String> validId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(1)
            .ofMaxLength(36);
    }

    @Provide
    Arbitrary<String> validName() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> validType() {
        return Arbitraries.of("person", "contact");
    }

    @Provide
    Arbitrary<String> optionalSex() {
        return Arbitraries.of("male", "female", null, "");
    }

    @Provide
    Arbitrary<String> optionalPhone() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.just(""),
            Arbitraries.strings().numeric().ofMinLength(7).ofMaxLength(15)
        );
    }
}
