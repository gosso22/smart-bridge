package com.smartbridge.core.model.fhir;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FHIRResourceWrapper, including new CHT resource types.
 */
class FHIRResourceWrapperTest {

    @Test
    void testForPatient() {
        Patient patient = new Patient();
        patient.setId("p-001");

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "UCS", "ucs-001");

        assertEquals(FHIRResourceWrapper.FHIRResourceType.PATIENT, wrapper.getResourceType());
        assertEquals("UCS", wrapper.getSourceSystem());
        assertEquals("ucs-001", wrapper.getOriginalId());
        assertNotNull(wrapper.getTransformedAt());
        assertTrue(wrapper.isValid());
    }

    @Test
    void testForEncounter() {
        Encounter encounter = new Encounter();
        encounter.setId("enc-001");

        FHIRResourceWrapper<Encounter> wrapper = FHIRResourceWrapper.forEncounter(encounter, "CHT", "cht-report-001");

        assertEquals(FHIRResourceWrapper.FHIRResourceType.ENCOUNTER, wrapper.getResourceType());
        assertEquals("CHT", wrapper.getSourceSystem());
        assertEquals("cht-report-001", wrapper.getOriginalId());
        assertTrue(wrapper.isValid());
    }

    @Test
    void testForOrganization() {
        Organization organization = new Organization();
        organization.setId("org-001");

        FHIRResourceWrapper<Organization> wrapper = FHIRResourceWrapper.forOrganization(organization, "CHT", "cht-clinic-001");

        assertEquals(FHIRResourceWrapper.FHIRResourceType.ORGANIZATION, wrapper.getResourceType());
        assertEquals("CHT", wrapper.getSourceSystem());
        assertEquals("cht-clinic-001", wrapper.getOriginalId());
        assertTrue(wrapper.isValid());
    }

    @Test
    void testForLocation() {
        Location location = new Location();
        location.setId("loc-001");

        FHIRResourceWrapper<Location> wrapper = FHIRResourceWrapper.forLocation(location, "CHT", "cht-clinic-001");

        assertEquals(FHIRResourceWrapper.FHIRResourceType.LOCATION, wrapper.getResourceType());
        assertEquals("CHT", wrapper.getSourceSystem());
        assertEquals("cht-clinic-001", wrapper.getOriginalId());
        assertTrue(wrapper.isValid());
    }

    @Test
    void testForObservation() {
        Observation observation = new Observation();
        observation.setId("obs-001");

        FHIRResourceWrapper<Observation> wrapper = FHIRResourceWrapper.forObservation(observation, "CHT", "cht-obs-001");

        assertEquals(FHIRResourceWrapper.FHIRResourceType.OBSERVATION, wrapper.getResourceType());
        assertTrue(wrapper.isValid());
    }

    @Test
    void testGetResourceId() {
        Patient patient = new Patient();
        patient.setId("p-123");

        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "UCS", "ucs-123");

        assertEquals("p-123", wrapper.getResourceId());
    }

    @Test
    void testGetResourceId_NullResource() {
        FHIRResourceWrapper<Patient> wrapper = new FHIRResourceWrapper<>();

        assertNull(wrapper.getResourceId());
        assertFalse(wrapper.isValid());
    }

    @Test
    void testSetResource_UpdatesResourceType() {
        FHIRResourceWrapper<Encounter> wrapper = new FHIRResourceWrapper<>();
        Encounter encounter = new Encounter();
        encounter.setId("enc-002");

        wrapper.setResource(encounter);

        assertEquals(FHIRResourceWrapper.FHIRResourceType.ENCOUNTER, wrapper.getResourceType());
    }

    @Test
    void testToString() {
        Organization org = new Organization();
        org.setId("org-001");

        FHIRResourceWrapper<Organization> wrapper = FHIRResourceWrapper.forOrganization(org, "CHT", "cht-org-001");

        String str = wrapper.toString();
        assertTrue(str.contains("ORGANIZATION"));
        assertTrue(str.contains("CHT"));
        assertTrue(str.contains("cht-org-001"));
    }

    @Test
    void testAllEnumValues() {
        FHIRResourceWrapper.FHIRResourceType[] types = FHIRResourceWrapper.FHIRResourceType.values();
        assertEquals(7, types.length);
        assertNotNull(FHIRResourceWrapper.FHIRResourceType.valueOf("ENCOUNTER"));
        assertNotNull(FHIRResourceWrapper.FHIRResourceType.valueOf("ORGANIZATION"));
        assertNotNull(FHIRResourceWrapper.FHIRResourceType.valueOf("LOCATION"));
    }
}
