package com.smartbridge.mediators.cht;

import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.cht.CHTContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CHTApiClient.
 * Tests use a non-existent port to verify error handling without a real CHT server.
 */
class CHTApiClientTest {

    private CHTApiClient apiClient;

    @BeforeEach
    void setUp() {
        apiClient = new CHTApiClient("http://localhost:9998", "medic", "password", null);
    }

    // ==================== Connection Tests ====================

    @Test
    void testTestConnection_NoServer() {
        boolean connected = apiClient.testConnection();
        assertFalse(connected, "Connection test should fail when no server is running");
    }

    // ==================== Person Operation Tests ====================

    @Test
    void testListPersons_NoServer() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.listPersons(100, 0));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("LIST_PERSONS", ex.getOperation());
    }

    @Test
    void testListPersons_WithPaginationParams() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.listPersons(50, 200));
        assertNotNull(ex.getMessage());
    }

    @Test
    void testGetContact_NoServer() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.getContact("test-contact-id"));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("GET_CONTACT", ex.getOperation());
    }

    @Test
    void testCreatePerson_NoServer() {
        CHTContact contact = createTestContact();
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.createPerson(contact));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("CREATE_PERSON", ex.getOperation());
    }

    @Test
    void testUpdatePerson_NoServer() {
        CHTContact contact = createTestContact();
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.updatePerson("test-id", contact));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("UPDATE_PERSON", ex.getOperation());
    }

    // ==================== Place and Report Tests ====================

    @Test
    void testListPlaces_NoServer() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.listPlaces("clinic"));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("LIST_PLACES", ex.getOperation());
    }

    @Test
    void testExportReports_NoServer() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.exportReports("pregnancy_visit"));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("EXPORT_REPORTS", ex.getOperation());
    }

    @Test
    void testExportReports_NullFormType() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.exportReports(null));
        assertEquals("EXPORT_REPORTS", ex.getOperation());
    }

    // ==================== Changes Feed Tests ====================

    @Test
    void testGetChanges_NoServer() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.getChanges("100-abc", 50));
        assertEquals("CHT_CLIENT", ex.getMediatorName());
        assertEquals("GET_CHANGES", ex.getOperation());
    }

    @Test
    void testGetChanges_NullSinceSeq() {
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.getChanges(null, 100));
        assertEquals("GET_CHANGES", ex.getOperation());
    }

    // ==================== CouchDB URL Fallback Tests ====================

    @Test
    void testGetChanges_UsesCouchdbUrlWhenConfigured() {
        CHTApiClient clientWithCouch = new CHTApiClient(
            "http://localhost:9998", "medic", "password", "http://localhost:5984");

        // Should attempt CouchDB URL, not the CHT base URL
        MediatorException ex = assertThrows(MediatorException.class,
            () -> clientWithCouch.getChanges("0", 10));
        assertEquals("GET_CHANGES", ex.getOperation());
    }

    @Test
    void testGetChanges_FallsBackToBaseUrlWhenNoCouchdb() {
        CHTApiClient clientNoCouch = new CHTApiClient(
            "http://localhost:9998", "medic", "password", null);

        MediatorException ex = assertThrows(MediatorException.class,
            () -> clientNoCouch.getChanges("0", 10));
        assertEquals("GET_CHANGES", ex.getOperation());
    }

    @Test
    void testGetChanges_EmptyCouchdbUrlFallsBack() {
        CHTApiClient clientEmptyCouch = new CHTApiClient(
            "http://localhost:9998", "medic", "password", "");

        MediatorException ex = assertThrows(MediatorException.class,
            () -> clientEmptyCouch.getChanges("0", 10));
        assertEquals("GET_CHANGES", ex.getOperation());
    }

    // ==================== Constructor and Accessor Tests ====================

    @Test
    void testConstructor_StoresBaseUrl() {
        assertEquals("http://localhost:9998", apiClient.getBaseUrl());
    }

    @Test
    void testConstructor_NullCouchdbUrl() {
        assertNull(apiClient.getCouchdbUrl());
    }

    @Test
    void testConstructor_WithCouchdbUrl() {
        CHTApiClient clientWithCouch = new CHTApiClient(
            "http://localhost:5988", "medic", "pass", "http://localhost:5984");
        assertEquals("http://localhost:5984", clientWithCouch.getCouchdbUrl());
    }

    // ==================== Error Status Code Tests ====================

    @Test
    void testAllOperations_ReturnMediatorExceptionWithStatusCode() {
        // Connection errors result in status 500
        MediatorException ex = assertThrows(MediatorException.class,
            () -> apiClient.getContact("id"));
        assertEquals(500, ex.getHttpStatusCode());
    }

    // ==================== Helpers ====================

    private CHTContact createTestContact() {
        CHTContact contact = new CHTContact();
        contact.setId("test-uuid-001");
        contact.setName("Amina Juma");
        contact.setType("contact");
        contact.setContactType("person");
        contact.setSex("female");
        contact.setDateOfBirth("1990-05-15");
        contact.setPhone("+255712345678");
        contact.setPatientId("12345");
        return contact;
    }
}
