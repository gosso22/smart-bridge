package com.smartbridge.config;

import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.RetryPolicy;
import com.smartbridge.mediators.cht.CHTApiClient;
import com.smartbridge.mediators.cht.ResilientCHTApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for resilient CHT client.
 */
@Configuration
public class ResilientCHTConfig {

    @Bean
    public ResilientCHTApiClient resilientCHTApiClient(
            CHTApiClient chtApiClient,
            @Qualifier("chtCircuitBreaker") CircuitBreaker chtCircuitBreaker,
            @Qualifier("chtRetryPolicy") RetryPolicy chtRetryPolicy) {
        return new ResilientCHTApiClient(chtApiClient, chtCircuitBreaker, chtRetryPolicy);
    }
}
