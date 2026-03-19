package com.smartbridge.mediators.ucs;

import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.ucs.UCSClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UCSApiClientTest {

    private UCSApiClient apiClient;

    @BeforeEach
    void setUp() {
        UCSApiClient.UCSAuthConfig authConfig = new UCSApiClient.UCSAuthConfig(
            UCSApiClient.AuthType.BASIC,
            "testuser",
            "testpass"
        );
        
        // Use non-existent port for tests
        apiClient = new UCSApiClient("http://localhost:9999/ucs", authConfig);
    }

    @Test
    void testCreateClient_NoServer() {
        UCSClient client = createTestClient();
        
        assertThrows(MediatorException.class, () -> {
            apiClient.createClient(client);
        });
    }

    @Test
    void testUpdateClient_NoServer() {
        UCSClient client = createTestClient();
        
        assertThrows(MediatorException.class, () -> {
            apiClient.updateClient("test-id", client);
        });
    }

    @Test
    void testGetClient_NoServer() {
        assertThrows(MediatorException.class, () -> {
            apiClient.getClient("test-id");
        });
    }

    @Test
    void testTestConnection_NoServer() {
        boolean connected = apiClient.testConnection();
        assertFalse(connected);
    }

    @Test
    void testTokenAuthConfig() {
        UCSApiClient.UCSAuthConfig authConfig = new UCSApiClient.UCSAuthConfig("test-token");
        
        assertEquals(UCSApiClient.AuthType.TOKEN, authConfig.getAuthType());
        assertEquals("test-token", authConfig.getToken());
    }

    @Test
    void testBasicAuthConfig() {
        UCSApiClient.UCSAuthConfig authConfig = new UCSApiClient.UCSAuthConfig(
            UCSApiClient.AuthType.BASIC,
            "user",
            "pass"
        );
        
        assertEquals(UCSApiClient.AuthType.BASIC, authConfig.getAuthType());
        assertEquals("user", authConfig.getUsername());
        assertEquals("pass", authConfig.getPassword());
    }

    @Test
    void testAuthConfigSetToken() {
        UCSApiClient.UCSAuthConfig authConfig = new UCSApiClient.UCSAuthConfig(
            UCSApiClient.AuthType.TOKEN,
            "user",
            "pass"
        );
        
        authConfig.setToken("new-token");
        assertEquals("new-token", authConfig.getToken());
    }

    private UCSClient createTestClient() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("test-entity-001");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "opensrp-123");
        client.setIdentifiers(identifiers);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("national_id", "national-456");
        client.setAttributes(attributes);

        client.setFirstName("John");
        client.setLastName("Doe");
        client.setGender("M");
        client.setBirthdate("1990-01-01");

        return client;
    }
}
