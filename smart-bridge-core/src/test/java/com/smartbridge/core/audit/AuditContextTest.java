package com.smartbridge.core.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditContextTest {

    @AfterEach
    void tearDown() {
        AuditContext.clearContext();
    }

    @Test
    void testSetAndGetContext() {
        // Arrange
        String userId = "user123";
        String userName = "Dr. Smith";
        String sourceIp = "192.168.1.100";

        // Act
        AuditContext.setContext(userId, userName, sourceIp);
        AuditContext.AuditContextData context = AuditContext.getContext();

        // Assert
        assertNotNull(context);
        assertEquals(userId, context.getUserId());
        assertEquals(userName, context.getUserName());
        assertEquals(sourceIp, context.getSourceIp());
    }

    @Test
    void testSetContextWithRequestId() {
        // Arrange
        String userId = "user123";
        String userName = "Dr. Smith";
        String sourceIp = "192.168.1.100";
        String requestId = "req-456";

        // Act
        AuditContext.setContext(userId, userName, sourceIp, requestId);
        AuditContext.AuditContextData context = AuditContext.getContext();

        // Assert
        assertNotNull(context);
        assertEquals(userId, context.getUserId());
        assertEquals(userName, context.getUserName());
        assertEquals(sourceIp, context.getSourceIp());
        assertEquals(requestId, context.getRequestId());
    }

    @Test
    void testGetContextWithoutSetting_ReturnsDefaultContext() {
        // Act
        AuditContext.AuditContextData context = AuditContext.getContext();

        // Assert
        assertNotNull(context);
        assertEquals("SYSTEM", context.getUserId());
        assertEquals("System", context.getUserName());
        assertEquals("localhost", context.getSourceIp());
    }

    @Test
    void testClearContext() {
        // Arrange
        AuditContext.setContext("user123", "Dr. Smith", "192.168.1.100");

        // Act
        AuditContext.clearContext();
        AuditContext.AuditContextData context = AuditContext.getContext();

        // Assert - should return default context after clearing
        assertEquals("SYSTEM", context.getUserId());
    }

    @Test
    void testGetUserId() {
        // Arrange
        AuditContext.setContext("user123", "Dr. Smith", "192.168.1.100");

        // Act
        String userId = AuditContext.getUserId();

        // Assert
        assertEquals("user123", userId);
    }

    @Test
    void testGetUserName() {
        // Arrange
        AuditContext.setContext("user123", "Dr. Smith", "192.168.1.100");

        // Act
        String userName = AuditContext.getUserName();

        // Assert
        assertEquals("Dr. Smith", userName);
    }

    @Test
    void testGetSourceIp() {
        // Arrange
        AuditContext.setContext("user123", "Dr. Smith", "192.168.1.100");

        // Act
        String sourceIp = AuditContext.getSourceIp();

        // Assert
        assertEquals("192.168.1.100", sourceIp);
    }

    @Test
    void testGetRequestId() {
        // Arrange
        AuditContext.setContext("user123", "Dr. Smith", "192.168.1.100", "req-789");

        // Act
        String requestId = AuditContext.getRequestId();

        // Assert
        assertEquals("req-789", requestId);
    }

    @Test
    void testThreadLocalIsolation() throws InterruptedException {
        // Arrange
        AuditContext.setContext("user1", "User One", "192.168.1.1");

        // Act - create a new thread with different context
        Thread thread = new Thread(() -> {
            AuditContext.setContext("user2", "User Two", "192.168.1.2");
            assertEquals("user2", AuditContext.getUserId());
        });
        thread.start();
        thread.join();

        // Assert - original thread should still have its context
        assertEquals("user1", AuditContext.getUserId());
    }
}
