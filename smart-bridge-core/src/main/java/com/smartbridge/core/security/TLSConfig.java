package com.smartbridge.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;

/**
 * TLS Configuration for secure external communications.
 * Configures SSL/TLS for all outbound HTTP connections to external systems.
 * 
 * Requirements: 8.1 - Encrypt all communications using TLS
 */
@Configuration
public class TLSConfig {

    @Value("${smartbridge.security.tls.enabled:true}")
    private boolean tlsEnabled;

    @Value("${smartbridge.security.tls.keystore-path:#{null}}")
    private String keystorePath;

    @Value("${smartbridge.security.tls.keystore-password:#{null}}")
    private String keystorePassword;

    @Value("${smartbridge.security.tls.truststore-path:#{null}}")
    private String truststorePath;

    @Value("${smartbridge.security.tls.truststore-password:#{null}}")
    private String truststorePassword;

    @Value("${smartbridge.security.tls.protocol:TLSv1.3}")
    private String tlsProtocol;

    @Value("${smartbridge.security.tls.verify-hostname:true}")
    private boolean verifyHostname;

    /**
     * Creates a RestTemplate configured with TLS/SSL settings.
     * 
     * @param builder RestTemplateBuilder for configuration
     * @return Configured RestTemplate with TLS enabled
     */
    @Bean
    public RestTemplate secureRestTemplate(RestTemplateBuilder builder) {
        if (!tlsEnabled) {
            return builder
                    .setConnectTimeout(Duration.ofSeconds(30))
                    .setReadTimeout(Duration.ofSeconds(30))
                    .build();
        }

        try {
            SSLContext sslContext = createSSLContext();
            ClientHttpRequestFactory requestFactory = createSecureRequestFactory(sslContext);
            
            return builder
                    .requestFactory(() -> requestFactory)
                    .setConnectTimeout(Duration.ofSeconds(30))
                    .setReadTimeout(Duration.ofSeconds(30))
                    .build();
        } catch (Exception e) {
            throw new SecurityConfigurationException("Failed to configure TLS for RestTemplate", e);
        }
    }

    /**
     * Creates SSL context with configured keystore and truststore.
     */
    private SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException,
            KeyStoreException, CertificateException, IOException, UnrecoverableKeyException {
        
        SSLContext sslContext = SSLContext.getInstance(tlsProtocol);
        
        KeyManager[] keyManagers = null;
        if (keystorePath != null && keystorePassword != null) {
            keyManagers = createKeyManagers();
        }
        
        TrustManager[] trustManagers = null;
        if (truststorePath != null && truststorePassword != null) {
            trustManagers = createTrustManagers();
        }
        
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        return sslContext;
    }

    /**
     * Creates key managers from configured keystore.
     */
    private KeyManager[] createKeyManagers() throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }
        
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
        
        return keyManagerFactory.getKeyManagers();
    }

    /**
     * Creates trust managers from configured truststore.
     */
    private TrustManager[] createTrustManagers() throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException {
        
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, truststorePassword.toCharArray());
        }
        
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        
        return trustManagerFactory.getTrustManagers();
    }

    /**
     * Creates a secure HTTP request factory with SSL context.
     */
    private ClientHttpRequestFactory createSecureRequestFactory(SSLContext sslContext) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) 
                    throws IOException {
                if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                    HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                    httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                    
                    if (!verifyHostname) {
                        httpsConnection.setHostnameVerifier((hostname, session) -> true);
                    }
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(30000);
        return factory;
    }
}
