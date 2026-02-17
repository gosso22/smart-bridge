package com.smartbridge.mediators.base;

import java.util.Map;

/**
 * Mediator registration information for OpenHIM Core.
 * Contains metadata and configuration for mediator registration.
 */
public class MediatorRegistration {

    private final String name;
    private final String version;
    private final String description;
    private final Map<String, String> endpoints;
    private final Map<String, Object> defaultChannelConfig;

    public MediatorRegistration(String name, String version, String description,
                              Map<String, String> endpoints, Map<String, Object> defaultChannelConfig) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.endpoints = endpoints;
        this.defaultChannelConfig = defaultChannelConfig;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getEndpoints() {
        return endpoints;
    }

    public Map<String, Object> getDefaultChannelConfig() {
        return defaultChannelConfig;
    }

    @Override
    public String toString() {
        return String.format(
            "MediatorRegistration{name='%s', version='%s', description='%s', endpoints=%s}",
            name, version, description, endpoints
        );
    }
}
