package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import net.jqwik.api.*;
import org.hl7.fhir.r4.model.Patient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for bidirectional CHT Person ↔ FHIR Patient transformation.
 * Feature: cht-integration, Property 3: Bidirectional Consistency
 *
 * Validates that the round-trip CHTContact → FHIR Patient → CHTContact preserves
 * all mapped fields: _id, patient_id, name, sex, date_of_birth, phone.
 */
class CHTPersonBidirectionalPropertyTest {

    private final CHTToFHIRPersonTransformer forwardTransformer = new CHTToFHIRPersonTransformer();
    private final FHIRToCHTPersonTransformer reverseTransformer = new FHIRToCHTPersonTransformer();

    /**
     * Property: Round-trip preserves _id for any valid contact.
     */
    @Property(tries = 50)
    void roundTripPreservesId(@ForAll("validContact") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals(original.getId(), roundTripped.getId(),
            "Round-trip must preserve _id");
    }

    /**
     * Property: Round-trip preserves patient_id when present.
     */
    @Property(tries = 50)
    void roundTripPreservesPatientId(@ForAll("contactWithPatientId") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals(original.getPatientId(), roundTripped.getPatientId(),
            "Round-trip must preserve patient_id");
    }

    /**
     * Property: Round-trip preserves name for multi-word names.
     * Single-word names are not invertible ("John" → given=John/family=John → "John John"),
     * so this property only applies to names with at least one space.
     */
    @Property(tries = 50)
    void roundTripPreservesName(@ForAll("validContact") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals(original.getName(), roundTripped.getName(),
            "Round-trip must preserve multi-word name");
    }

    /**
     * Property: Round-trip preserves sex for CHT-recognized gender values (male/female).
     */
    @Property(tries = 50)
    void roundTripPreservesSex(@ForAll("contactWithSex") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals(original.getSex(), roundTripped.getSex(),
            "Round-trip must preserve sex (male/female)");
    }

    /**
     * Property: Round-trip preserves date_of_birth for valid ISO dates.
     */
    @Property(tries = 50)
    void roundTripPreservesBirthDate(@ForAll("contactWithBirthDate") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals(original.getDateOfBirth(), roundTripped.getDateOfBirth(),
            "Round-trip must preserve date_of_birth");
    }

    /**
     * Property: Round-trip preserves phone when present.
     */
    @Property(tries = 50)
    void roundTripPreservesPhone(@ForAll("contactWithPhone") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals(original.getPhone(), roundTripped.getPhone(),
            "Round-trip must preserve phone");
    }

    /**
     * Property: Round-trip preserves parent reference (_id) when present.
     */
    @Property(tries = 50)
    void roundTripPreservesParentRef(@ForAll("contactWithParent") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertNotNull(roundTripped.getParent(), "Round-trip must preserve parent");
        assertEquals(original.getParent().getId(), roundTripped.getParent().getId(),
            "Round-trip must preserve parent._id");
    }

    /**
     * Property: Forward transform always produces a valid FHIR Patient with CHT source tag.
     */
    @Property(tries = 50)
    void forwardTransformAlwaysProducesValidPatient(@ForAll("validContact") CHTContact contact) throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = forwardTransformer.transform(contact);

        assertNotNull(wrapper, "Wrapper must not be null");
        assertNotNull(wrapper.getResource(), "Patient must not be null");
        assertEquals("CHT", wrapper.getSourceSystem(), "Source system must be CHT");
        assertEquals(contact.getId(), wrapper.getOriginalId(), "Original ID must match contact _id");
        assertTrue(wrapper.getResource().hasIdentifier(), "Patient must have identifiers");
        assertTrue(wrapper.getResource().hasName(), "Patient must have a name");
    }

    /**
     * Property: Reverse transform always sets CHT type fields.
     */
    @Property(tries = 50)
    void reverseTransformAlwaysSetsTypeFields(@ForAll("validContact") CHTContact original) throws TransformationException {
        CHTContact roundTripped = roundTrip(original);

        assertEquals("contact", roundTripped.getType(), "type must be 'contact'");
        assertEquals("person", roundTripped.getContactType(), "contact_type must be 'person'");
    }

    // ==================== Helper ====================

    private CHTContact roundTrip(CHTContact original) throws TransformationException {
        FHIRResourceWrapper<Patient> wrapper = forwardTransformer.transform(original);
        return reverseTransformer.transform(wrapper);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<CHTContact> validContact() {
        return Combinators.combine(
            validId(),
            validMultiWordName(),
            validSex(),
            validBirthDate(),
            validPhone(),
            validPatientId(),
            validParentRef()
        ).as((id, name, sex, dob, phone, patientId, parent) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setSex(sex);
            c.setDateOfBirth(dob);
            c.setPhone(phone);
            c.setPatientId(patientId);
            c.setParent(parent);
            c.setType("contact");
            c.setContactType("person");
            return c;
        });
    }

    @Provide
    Arbitrary<CHTContact> contactWithPatientId() {
        return Combinators.combine(
            validId(),
            validMultiWordName(),
            validId() // patient_id
        ).as((id, name, patientId) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setPatientId(patientId);
            return c;
        });
    }

    @Provide
    Arbitrary<CHTContact> contactWithSex() {
        return Combinators.combine(
            validId(),
            validMultiWordName(),
            Arbitraries.of("male", "female")
        ).as((id, name, sex) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setSex(sex);
            return c;
        });
    }

    @Provide
    Arbitrary<CHTContact> contactWithBirthDate() {
        return Combinators.combine(
            validId(),
            validMultiWordName(),
            validBirthDate()
        ).as((id, name, dob) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setDateOfBirth(dob);
            return c;
        });
    }

    @Provide
    Arbitrary<CHTContact> contactWithPhone() {
        return Combinators.combine(
            validId(),
            validMultiWordName(),
            validPhone()
        ).as((id, name, phone) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setPhone(phone);
            return c;
        });
    }

    @Provide
    Arbitrary<CHTContact> contactWithParent() {
        return Combinators.combine(
            validId(),
            validMultiWordName(),
            validParentRef()
        ).as((id, name, parent) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(name);
            c.setParent(parent);
            return c;
        });
    }

    // ==================== Atomic Generators ====================

    private Arbitrary<String> validId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(36);
    }

    /**
     * Generates multi-word names (given + family) that survive the round-trip.
     * Single-word names are NOT invertible in this transformer pair.
     */
    private Arbitrary<String> validMultiWordName() {
        Arbitrary<String> given = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> family = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        return Combinators.combine(given, family).as((g, f) -> g + " " + f);
    }

    private Arbitrary<String> validSex() {
        return Arbitraries.of("male", "female");
    }

    private Arbitrary<String> validBirthDate() {
        return Arbitraries.longs()
            .between(
                LocalDate.of(1920, 1, 1).toEpochDay(),
                LocalDate.of(2025, 12, 31).toEpochDay()
            )
            .map(epochDay -> LocalDate.ofEpochDay(epochDay).toString());
    }

    private Arbitrary<String> validPhone() {
        return Arbitraries.strings()
            .numeric()
            .withChars('+', '-')
            .ofMinLength(7)
            .ofMaxLength(15);
    }

    private Arbitrary<String> validPatientId() {
        return Arbitraries.strings()
            .numeric()
            .ofMinLength(3)
            .ofMaxLength(10);
    }

    private Arbitrary<CHTContact.CHTParentRef> validParentRef() {
        return validId().map(id -> {
            CHTContact.CHTParentRef ref = new CHTContact.CHTParentRef();
            ref.setId(id);
            return ref;
        });
    }
}