package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import net.jqwik.api.*;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CHT Place to FHIR Organization/Location transformation.
 * Feature: cht-integration, Property 4: Place Hierarchy Preservation
 *
 * Validates that for any valid CHT place hierarchy, the Organization.partOf chain
 * is structurally equivalent and resource counts are correct.
 */
class CHTPlaceHierarchyPropertyTest {

    private final CHTToFHIRPlaceTransformer transformer = new CHTToFHIRPlaceTransformer();

    /**
     * Property: Every valid place produces at least one resource (Organization).
     */
    @Property(tries = 50)
    void transformAlwaysProducesOrganization(@ForAll("validPlace") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertFalse(result.isEmpty(), "Must produce at least one resource");
        assertTrue(result.get(0).getResource() instanceof Organization,
            "First resource must be Organization");
    }

    /**
     * Property: Organization identifier always matches the place _id.
     */
    @Property(tries = 50)
    void organizationIdentifierMatchesPlaceId(@ForAll("validPlace") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);
        Organization org = (Organization) result.get(0).getResource();

        assertTrue(org.hasIdentifier(), "Organization must have identifiers");
        boolean hasMatchingId = org.getIdentifier().stream()
            .anyMatch(id ->
                CHTToFHIRPlaceTransformer.CHT_PLACE_UUID_SYSTEM.equals(id.getSystem())
                && place.getId().equals(id.getValue()));
        assertTrue(hasMatchingId, "Organization identifier must match place _id");
    }

    /**
     * Property: Organization name always matches the place name.
     */
    @Property(tries = 50)
    void organizationNameMatchesPlaceName(@ForAll("validPlace") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);
        Organization org = (Organization) result.get(0).getResource();

        assertEquals(place.getName(), org.getName(),
            "Organization.name must match place.name");
    }

    /**
     * Property: When a place has a parent, Organization.partOf references it correctly.
     */
    @Property(tries = 50)
    void parentRefMapsToPartOf(@ForAll("placeWithParent") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);
        Organization org = (Organization) result.get(0).getResource();

        assertTrue(org.hasPartOf(), "Organization must have partOf when place has parent");
        assertEquals("Organization/" + place.getParent().getId(), org.getPartOf().getReference(),
            "partOf reference must match parent._id");
    }

    /**
     * Property: When a place has no parent, Organization.partOf is absent.
     */
    @Property(tries = 50)
    void noParentMeansNoPartOf(@ForAll("placeWithoutParent") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);
        Organization org = (Organization) result.get(0).getResource();

        assertFalse(org.hasPartOf(), "Organization must NOT have partOf when place has no parent");
    }

    /**
     * Property: Known contact_types always map to the expected Organization type code.
     */
    @Property(tries = 20)
    void knownContactTypeMapsToCorrectOrgType(
            @ForAll("placeWithKnownContactType") CHTPlace place) throws TransformationException {

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);
        Organization org = (Organization) result.get(0).getResource();

        assertTrue(org.hasType(), "Organization must have type");
        String code = org.getType().get(0).getCoding().get(0).getCode();

        String expected;
        switch (place.getContactType()) {
            case "district_hospital": expected = "prov"; break;
            case "health_center": expected = "dept"; break;
            case "clinic": expected = "team"; break;
            default: expected = place.getContactType(); break;
        }
        assertEquals(expected, code,
            "Organization type code must match mapping for " + place.getContactType());
    }

    /**
     * Property: Place with valid geolocation produces exactly 2 resources (Organization + Location).
     */
    @Property(tries = 50)
    void validGeolocationProducesOrganizationAndLocation(
            @ForAll("placeWithGeolocation") CHTPlace place) throws TransformationException {

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertEquals(2, result.size(),
            "Place with valid geolocation must produce 2 resources");
        assertTrue(result.get(0).getResource() instanceof Organization);
        assertTrue(result.get(1).getResource() instanceof Location);
    }

    /**
     * Property: Place without geolocation produces exactly 1 resource (Organization only).
     */
    @Property(tries = 50)
    void noGeolocationProducesOrganizationOnly(@ForAll("placeWithoutGeolocation") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertEquals(1, result.size(),
            "Place without geolocation must produce 1 resource");
        assertTrue(result.get(0).getResource() instanceof Organization);
    }

    /**
     * Property: Location's managingOrganization references the same place _id.
     */
    @Property(tries = 50)
    void locationReferencesItsOrganization(@ForAll("placeWithGeolocation") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        Location location = (Location) result.get(1).getResource();
        assertEquals("Organization/" + place.getId(),
            location.getManagingOrganization().getReference(),
            "Location.managingOrganization must reference the place's Organization");
    }

    /**
     * Property: Location position coordinates match the parsed geolocation.
     */
    @Property(tries = 50)
    void locationPositionMatchesGeolocation(@ForAll("placeWithGeolocation") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        Location location = (Location) result.get(1).getResource();
        String[] parts = place.getGeolocation().trim().split("\\s+");
        double expectedLat = Double.parseDouble(parts[0]);
        double expectedLon = Double.parseDouble(parts[1]);

        assertEquals(expectedLat, location.getPosition().getLatitude().doubleValue(), 0.0001,
            "Latitude must match");
        assertEquals(expectedLon, location.getPosition().getLongitude().doubleValue(), 0.0001,
            "Longitude must match");
    }

    /**
     * Property: Source system is always "CHT" for all produced resources.
     */
    @Property(tries = 50)
    void sourceSystemAlwaysCHT(@ForAll("validPlace") CHTPlace place) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        for (FHIRResourceWrapper<? extends Resource> wrapper : result) {
            assertEquals("CHT", wrapper.getSourceSystem(),
                "Source system must be CHT for all resources");
        }
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<CHTPlace> validPlace() {
        return Combinators.combine(
            validId(),
            validName(),
            validContactType(),
            Arbitraries.of(true, false), // has parent
            Arbitraries.of(true, false)  // has geolocation
        ).as((id, name, contactType, hasParent, hasGeo) -> {
            CHTPlace place = buildBasePlace(id, name, contactType);
            if (hasParent) {
                place.setParent(buildParentRef());
            }
            if (hasGeo) {
                place.setGeolocation(randomGeo());
            }
            return place;
        });
    }

    @Provide
    Arbitrary<CHTPlace> placeWithParent() {
        return Combinators.combine(
            validId(),
            validName(),
            validContactType(),
            validId() // parent id
        ).as((id, name, contactType, parentId) -> {
            CHTPlace place = buildBasePlace(id, name, contactType);
            CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
            parent.setId(parentId);
            place.setParent(parent);
            return place;
        });
    }

    @Provide
    Arbitrary<CHTPlace> placeWithoutParent() {
        return Combinators.combine(
            validId(),
            validName(),
            validContactType()
        ).as((id, name, contactType) -> buildBasePlace(id, name, contactType));
    }

    @Provide
    Arbitrary<CHTPlace> placeWithKnownContactType() {
        return Combinators.combine(
            validId(),
            validName(),
            Arbitraries.of("district_hospital", "health_center", "clinic")
        ).as((id, name, contactType) -> buildBasePlace(id, name, contactType));
    }

    @Provide
    Arbitrary<CHTPlace> placeWithGeolocation() {
        return Combinators.combine(
            validId(),
            validName(),
            validContactType(),
            validLatitude(),
            validLongitude()
        ).as((id, name, contactType, lat, lon) -> {
            CHTPlace place = buildBasePlace(id, name, contactType);
            place.setGeolocation(lat + " " + lon);
            return place;
        });
    }

    @Provide
    Arbitrary<CHTPlace> placeWithoutGeolocation() {
        return Combinators.combine(
            validId(),
            validName(),
            validContactType()
        ).as((id, name, contactType) -> {
            CHTPlace place = buildBasePlace(id, name, contactType);
            place.setGeolocation(null);
            return place;
        });
    }

    // ==================== Helpers ====================

    private CHTPlace buildBasePlace(String id, String name, String contactType) {
        CHTPlace place = new CHTPlace();
        place.setId(id);
        place.setName(name);
        place.setContactType(contactType);
        place.setType("contact");
        return place;
    }

    private CHTContact.CHTParentRef buildParentRef() {
        CHTContact.CHTParentRef ref = new CHTContact.CHTParentRef();
        ref.setId("parent-" + System.nanoTime());
        return ref;
    }

    private String randomGeo() {
        double lat = -6.0 + Math.random() * 2;
        double lon = 35.0 + Math.random() * 5;
        return String.format("%.6f %.6f", lat, lon);
    }

    private Arbitrary<String> validId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(36);
    }

    private Arbitrary<String> validName() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(2)
            .ofMaxLength(40);
    }

    private Arbitrary<String> validContactType() {
        return Arbitraries.of("district_hospital", "health_center", "clinic");
    }

    private Arbitrary<Double> validLatitude() {
        return Arbitraries.doubles().between(-90.0, 90.0);
    }

    private Arbitrary<Double> validLongitude() {
        return Arbitraries.doubles().between(-180.0, 180.0);
    }
}