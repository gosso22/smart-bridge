package com.smartbridge.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from .env file into Spring's environment.
 */
public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(DotenvConfig.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            // Try to find .env file in project root
            String projectRoot = System.getProperty("user.dir");
            
            // When running with Maven, we might be in a submodule directory
            if (projectRoot.endsWith("smart-bridge-application")) {
                projectRoot = projectRoot.substring(0, projectRoot.lastIndexOf("/smart-bridge-application"));
            }
            
            Dotenv dotenv = Dotenv.configure()
                    .directory(projectRoot)
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .systemProperties()
                    .load();

            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            Map<String, Object> dotenvProperties = new HashMap<>();

            dotenv.entries().forEach(entry -> {
                // Only add properties from the actual .env file, not system env vars
                String key = entry.getKey();
                if (!key.matches("^[A-Z_]+$") || key.startsWith("UCS_") || key.startsWith("FHIR_") || 
                    key.startsWith("OPENHIM_") || key.startsWith("RABBITMQ_") || key.startsWith("SSL_") || 
                    key.startsWith("TLS_") || key.startsWith("ENCRYPTION_") || key.startsWith("METRICS_") || 
                    key.startsWith("AUDIT_") || key.startsWith("TRANSFORMATION_") || key.startsWith("CIRCUIT_") || 
                    key.startsWith("RETRY_") || key.startsWith("NETWORK_")) {
                    dotenvProperties.put(key, entry.getValue());
                    logger.debug("Loaded .env property: {} = {}", key, entry.getValue());
                }
            });

            environment.getPropertySources().addFirst(
                    new MapPropertySource("dotenvProperties", dotenvProperties)
            );

            logger.info("Loaded {} properties from .env file at {}", dotenvProperties.size(), projectRoot);
        } catch (Exception e) {
            logger.warn("Could not load .env file: {}", e.getMessage());
        }
    }
}
