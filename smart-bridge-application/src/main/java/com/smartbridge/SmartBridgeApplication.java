package com.smartbridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Main Spring Boot application class for Smart Bridge Interoperability Solution.
 * 
 * This application provides a hub-and-spoke architecture connecting legacy health systems
 * (UCS and GoTHOMIS) with modern FHIR-based ecosystems using OpenHIM as the central
 * routing and transformation engine.
 */
@SpringBootApplication(scanBasePackages = "com.smartbridge")
@EnableConfigurationProperties
@EnableScheduling
@EnableAsync
public class SmartBridgeApplication {

    private static final Logger logger = LoggerFactory.getLogger(SmartBridgeApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication app = new SpringApplication(SmartBridgeApplication.class);
            app.setBannerMode(Banner.Mode.LOG);
            app.setRegisterShutdownHook(true);
            
            ConfigurableApplicationContext context = app.run(args);
            logApplicationStartup(context.getEnvironment());
        } catch (Exception e) {
            logger.error("Failed to start Smart Bridge application", e);
            System.exit(1);
        }
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = env.getProperty("server.ssl.enabled", "false").equals("true") ? "https" : "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";
        
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("Unable to determine host address");
        }

        String profile = env.getProperty("spring.profiles.active", "default");

        logger.info("\n----------------------------------------------------------\n" +
                "  Smart Bridge Interoperability Solution is running!\n" +
                "  Profile:     {}\n" +
                "  Local:       {}://localhost:{}{}\n" +
                "  External:    {}://{}:{}{}\n" +
                "  Health:      {}://localhost:{}{}/actuator/health\n" +
                "  Metrics:     {}://localhost:{}{}/actuator/metrics\n" +
                "  Prometheus:  {}://localhost:{}{}/actuator/prometheus\n" +
                "----------------------------------------------------------",
                profile,
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath
        );
    }
}