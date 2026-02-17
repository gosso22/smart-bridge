package com.smartbridge.mediators.base;

/**
 * Client interface for OpenHIM Core interactions.
 * Handles mediator registration, heartbeat, and transaction reporting.
 */
public interface OpenHIMClient {

    /**
     * Register a mediator with OpenHIM Core.
     * 
     * @param registration Mediator registration details
     * @return true if registration successful, false otherwise
     */
    boolean registerMediator(MediatorRegistration registration);

    /**
     * Send heartbeat to OpenHIM Core to indicate mediator is alive.
     * 
     * @param mediatorName Name of the mediator
     * @return true if heartbeat acknowledged, false otherwise
     */
    boolean sendHeartbeat(String mediatorName);

    /**
     * Report transaction to OpenHIM Core for monitoring and audit.
     * 
     * @param transaction Transaction details
     * @return true if transaction reported successfully, false otherwise
     */
    boolean reportTransaction(TransactionReport transaction);

    /**
     * Check if OpenHIM Core is reachable.
     * 
     * @return true if OpenHIM Core is reachable, false otherwise
     */
    boolean isOpenHIMReachable();
}
