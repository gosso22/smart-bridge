package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CHTToFHIRPlaceTransformerTest {

    private CHTToFHIRPlaceTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CHTToFHIRPlaceTransformer();
    }

    @Test
    void testTransform_ValidPlace_WithGeolocation() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation("-6.8235 39.2695");

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertEquals(2, result.size());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.ORGANIZATION, result.get(0).getResourceType());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.LOCATION, result.get(1).getResourceType());
    }

    @Test
    void testTransform_ValidPlace_WithoutGeolocation() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation(null);

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertEquals(1, result.size());
        assertEquals(FHIRResourceWrapper.FHIRResourceType.ORGANIZATION, result.get(0).getResourceType());
    }

    @Test
    void testTransform_OrganizationIdentifier() throws TransformationException {
        CHTPlace place = createValidPlace();

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);
        Organization org = (Organization) result.get(0).getResource();

        assertEquals(1, org.getIdentifier().size());
        assertEquals(CHTToFHIRPlaceTransformer.CHT_PLACE_UUID_SYSTEM, org.getIdentifier().get(0).getSystem());
        assertEquals("cht-clinic-001", org.getIdentifier().get(0).getValue());
    }

    @Test
    void testTransform_OrganizationName() throws TransformationException {
        CHTPlace place = createValidPlace();

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertEquals("Kariakoo Clinic", org.getName());
    }

    @Test
    void testTransform_OrganizationType_Clinic() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setContactType("clinic");

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertEquals("team", org.getType().get(0).getCodingFirstRep().getCode());
    }

    @Test
    void testTransform_OrganizationType_HealthCenter() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setContactType("health_center");

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertEquals("dept", org.getType().get(0).getCodingFirstRep().getCode());
    }

    @Test
    void testTransform_OrganizationType_DistrictHospital() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setContactType("district_hospital");

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertEquals("prov", org.getType().get(0).getCodingFirstRep().getCode());
    }

    @Test
    void testTransform_OrganizationType_Unknown() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setContactType("custom_facility");

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertEquals("custom_facility", org.getType().get(0).getCodingFirstRep().getCode());
    }

    @Test
    void testTransform_PartOf_Hierarchy() throws TransformationException {
        CHTPlace place = createValidPlace();
        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("cht-hc-001");
        parent.setContactType("health_center");
        place.setParent(parent);

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertTrue(org.hasPartOf());
        assertEquals("Organization/cht-hc-001", org.getPartOf().getReference());
    }

    @Test
    void testTransform_PartOf_NoParent() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setParent(null);

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertFalse(org.hasPartOf());
    }

    @Test
    void testTransform_PrimaryContact() throws TransformationException {
        CHTPlace place = createValidPlace();
        CHTPlace.CHTContactRef contactRef = new CHTPlace.CHTContactRef();
        contactRef.setId("cht-person-001");
        contactRef.setName("Nurse Amina");
        place.setContact(contactRef);

        Organization org = (Organization) transformer.transform(place).get(0).getResource();

        assertEquals(1, org.getContact().size());
        assertEquals("Nurse Amina", org.getContact().get(0).getName().getText());
    }

    @Test
    void testTransform_LocationPosition() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation("-6.8235 39.2695");

        Location loc = (Location) transformer.transform(place).get(1).getResource();

        assertNotNull(loc.getPosition());
        assertEquals(-6.8235, loc.getPosition().getLatitude().doubleValue(), 0.0001);
        assertEquals(39.2695, loc.getPosition().getLongitude().doubleValue(), 0.0001);
    }

    @Test
    void testTransform_LocationName() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation("-6.8235 39.2695");

        Location loc = (Location) transformer.transform(place).get(1).getResource();

        assertEquals("Kariakoo Clinic", loc.getName());
    }

    @Test
    void testTransform_LocationManagingOrganization() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation("-6.8235 39.2695");

        Location loc = (Location) transformer.transform(place).get(1).getResource();

        assertEquals("Organization/cht-clinic-001", loc.getManagingOrganization().getReference());
    }

    @Test
    void testTransform_InvalidGeolocation() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation("not-a-coordinate");

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        // Should still produce Organization, but skip Location
        assertEquals(1, result.size());
    }

    @Test
    void testTransform_EmptyGeolocation() throws TransformationException {
        CHTPlace place = createValidPlace();
        place.setGeolocation("   ");

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertEquals(1, result.size());
    }

    @Test
    void testTransform_NullPlace() {
        assertThrows(TransformationException.class, () -> transformer.transform(null));
    }

    @Test
    void testTransform_MissingId() {
        CHTPlace place = createValidPlace();
        place.setId(null);

        assertThrows(TransformationException.class, () -> transformer.transform(place));
    }

    @Test
    void testTransform_MissingName() {
        CHTPlace place = createValidPlace();
        place.setName(null);

        assertThrows(TransformationException.class, () -> transformer.transform(place));
    }

    @Test
    void testTransform_SourceSystem() throws TransformationException {
        CHTPlace place = createValidPlace();

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(place);

        assertEquals("CHT", result.get(0).getSourceSystem());
        assertEquals("cht-clinic-001", result.get(0).getOriginalId());
    }

    // Helper
    private CHTPlace createValidPlace() {
        CHTPlace place = new CHTPlace();
        place.setId("cht-clinic-001");
        place.setRev("1-def456");
        place.setType("contact");
        place.setContactType("clinic");
        place.setName("Kariakoo Clinic");
        place.setReportedDate(1680000000000L);
        return place;
    }
}
