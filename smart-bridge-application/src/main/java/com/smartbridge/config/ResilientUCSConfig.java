package com.smartbridge.config;

import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.RetryPolicy;
import com.smartbridge.mediators.ucs.ResilientUCSApiClient;
import com.smartbridge.mediators.ucs.UCSApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for resilient UCS client.
 */
@Configuration
public class ResilientUCSConfig {

    @Bean
    public ResilientUCSApiClient resilientUCSApiClient(
            UCSApiClient ucsApiClient,
            @Qualifier("ucsCircuitBreaker") CircuitBreaker ucsCircuitBreaker,
            @Qualifier("ucsRetryPolicy") RetryPolicy ucsRetryPolicy) {
        return new ResilientUCSApiClient(ucsApiClient, ucsCircuitBreaker, ucsRetryPolicy);
    }
}
