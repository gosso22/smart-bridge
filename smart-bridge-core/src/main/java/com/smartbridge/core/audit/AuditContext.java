package com.smartbridge.core.audit;

/**
 * Thread-local context for audit logging.
 * Captures user identification and request context for comprehensive audit trails.
 */
public class AuditContext {
    
    private static final ThreadLocal<AuditContextData> contextHolder = new ThreadLocal<>();

    /**
     * Set audit context for current thread.
     */
    public static void setContext(String userId, String userName, String sourceIp) {
        contextHolder.set(new AuditContextData(userId, userName, sourceIp));
    }

    /**
     * Set audit context with request ID.
     */
    public static void setContext(String userId, String userName, String sourceIp, String requestId) {
        contextHolder.set(new AuditContextData(userId, userName, sourceIp, requestId));
    }

    /**
     * Get current audit context.
     */
    public static AuditContextData getContext() {
        AuditContextData context = contextHolder.get();
        if (context == null) {
            // Return default context for system operations
            return new AuditContextData("SYSTEM", "System", "localhost");
        }
        return context;
    }

    /**
     * Clear audit context for current thread.
     */
    public static void clearContext() {
        contextHolder.remove();
    }

    /**
     * Get user ID from current context.
     */
    public static String getUserId() {
        return getContext().getUserId();
    }

    /**
     * Get user name from current context.
     */
    public static String getUserName() {
        return getContext().getUserName();
    }

    /**
     * Get source IP from current context.
     */
    public static String getSourceIp() {
        return getContext().getSourceIp();
    }

    /**
     * Get request ID from current context.
     */
    public static String getRequestId() {
        return getContext().getRequestId();
    }

    /**
     * Data holder for audit context information.
     */
    public static class AuditContextData {
        private final String userId;
        private final String userName;
        private final String sourceIp;
        private final String requestId;

        public AuditContextData(String userId, String userName, String sourceIp) {
            this(userId, userName, sourceIp, null);
        }

        public AuditContextData(String userId, String userName, String sourceIp, String requestId) {
            this.userId = userId;
            this.userName = userName;
            this.sourceIp = sourceIp;
            this.requestId = requestId;
        }

        public String getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getRequestId() {
            return requestId;
        }
    }
}
