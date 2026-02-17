package com.smartbridge.core.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for audit logging infrastructure.
 * Registers audit interceptor for automatic context capture.
 */
@Configuration
public class AuditConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    public AuditConfig(AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/health/**");
    }
}
