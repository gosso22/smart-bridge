package com.smartbridge.mediators.cht;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.cht.CHTChangesResponse;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;

/**
 * Client for interacting with the CHT (Community Health Toolkit) API.
 * Supports Basic Authentication (CHT standard) and optional direct CouchDB access
 * for the {@code _changes} feed.
 */
public class CHTApiClient {

    private static final Logger logger = LoggerFactory.getLogger(CHTApiClient.class);
    private static final String COMPONENT = "CHT_CLIENT";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String couchdbUrl;

    /**
     * @param baseUrl   CHT API base URL (e.g., http://localhost:5988)
     * @param username  CHT Basic Auth username
     * @param password  CHT Basic Auth password
     * @param couchdbUrl Optional direct CouchDB URL for _changes feed; null to use CHT API proxy
     */
    public CHTApiClient(String baseUrl, String username, String password, String couchdbUrl) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.couchdbUrl = couchdbUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    /**
     * List person contacts with pagination.
     * GET /api/v1/person?limit={limit}&skip={skip}
     */
    @SuppressWarnings("unchecked")
    public List<CHTContact> listPersons(int limit, int skip) throws MediatorException {
        String url = baseUrl + "/api/v1/person?limit=" + limit + "&skip=" + skip;

        try {
            logger.info("Listing persons from CHT: limit={}, skip={}", limit, skip);

            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

            List<CHTContact> contacts = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CHTContact.class));

            logger.info("Retrieved {} persons from CHT", contacts.size());
            return contacts;

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error listing persons from CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to list persons from CHT: " + e.getMessage(),
                e, COMPONENT, "LIST_PERSONS", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error listing persons from CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error listing persons from CHT: " + e.getMessage(),
                e, COMPONENT, "LIST_PERSONS", 500);
        }
    }

    /**
     * Get a single contact by ID.
     * GET /api/v1/contact/{id}
     */
    public CHTContact getContact(String id) throws MediatorException {
        String url = baseUrl + "/api/v1/contact/" + id;

        try {
            logger.info("Retrieving contact from CHT: id={}", id);

            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<CHTContact> response = restTemplate.exchange(
                url, HttpMethod.GET, request, CHTContact.class);

            logger.info("Successfully retrieved contact from CHT: id={}", id);
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error retrieving contact from CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to retrieve contact from CHT: " + e.getMessage(),
                e, COMPONENT, "GET_CONTACT", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error retrieving contact from CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error retrieving contact from CHT: " + e.getMessage(),
                e, COMPONENT, "GET_CONTACT", 500);
        }
    }

    /**
     * Create a new person contact.
     * POST /api/v1/people
     */
    public CHTContact createPerson(CHTContact contact) throws MediatorException {
        String url = baseUrl + "/api/v1/people";

        try {
            logger.info("Creating person in CHT: name={}", contact.getName());

            HttpEntity<CHTContact> request = new HttpEntity<>(contact, buildHeaders());
            ResponseEntity<CHTContact> response = restTemplate.exchange(
                url, HttpMethod.POST, request, CHTContact.class);

            logger.info("Successfully created person in CHT: status={}", response.getStatusCode());
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error creating person in CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to create person in CHT: " + e.getMessage(),
                e, COMPONENT, "CREATE_PERSON", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error creating person in CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error creating person in CHT: " + e.getMessage(),
                e, COMPONENT, "CREATE_PERSON", 500);
        }
    }

    /**
     * Update an existing person contact.
     * PUT /api/v1/people/{id}
     */
    public CHTContact updatePerson(String id, CHTContact contact) throws MediatorException {
        String url = baseUrl + "/api/v1/people/" + id;

        try {
            logger.info("Updating person in CHT: id={}", id);

            HttpEntity<CHTContact> request = new HttpEntity<>(contact, buildHeaders());
            ResponseEntity<CHTContact> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, CHTContact.class);

            logger.info("Successfully updated person in CHT: status={}", response.getStatusCode());
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error updating person in CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to update person in CHT: " + e.getMessage(),
                e, COMPONENT, "UPDATE_PERSON", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error updating person in CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error updating person in CHT: " + e.getMessage(),
                e, COMPONENT, "UPDATE_PERSON", 500);
        }
    }

    /**
     * Export contacts by type.
     * GET /api/v1/export/contacts?type={contactType}
     */
    public String listPlaces(String contactType) throws MediatorException {
        String url = baseUrl + "/api/v1/export/contacts?type=" + contactType;

        try {
            logger.info("Exporting places from CHT: contactType={}", contactType);

            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

            logger.info("Successfully exported places from CHT");
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error exporting places from CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to export places from CHT: " + e.getMessage(),
                e, COMPONENT, "LIST_PLACES", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error exporting places from CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error exporting places from CHT: " + e.getMessage(),
                e, COMPONENT, "LIST_PLACES", 500);
        }
    }

    /**
     * Export reports, optionally filtered by form type.
     * GET /api/v1/export/reports
     */
    public String exportReports(String formType) throws MediatorException {
        String url = baseUrl + "/api/v1/export/reports";
        if (formType != null && !formType.isEmpty()) {
            url += "?form=" + formType;
        }

        try {
            logger.info("Exporting reports from CHT: formType={}", formType);

            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

            logger.info("Successfully exported reports from CHT");
            return response.getBody();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error exporting reports from CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to export reports from CHT: " + e.getMessage(),
                e, COMPONENT, "EXPORT_REPORTS", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error exporting reports from CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error exporting reports from CHT: " + e.getMessage(),
                e, COMPONENT, "EXPORT_REPORTS", 500);
        }
    }

    /**
     * Fetch incremental changes from the CouchDB _changes feed.
     * Uses direct CouchDB URL when configured, otherwise falls back to CHT API proxy.
     * GET medic/_changes?since={sinceSeq}&limit={limit}&include_docs=true
     */
    public CHTChangesResponse getChanges(String sinceSeq, int limit) throws MediatorException {
        String changesBaseUrl = (couchdbUrl != null && !couchdbUrl.isEmpty())
            ? couchdbUrl : baseUrl;
        String url = changesBaseUrl + "/medic/_changes?include_docs=true&limit=" + limit;
        if (sinceSeq != null && !sinceSeq.isEmpty()) {
            url += "&since=" + sinceSeq;
        }

        try {
            logger.info("Fetching changes from CHT: sinceSeq={}, limit={}", sinceSeq, limit);

            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<CHTChangesResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, CHTChangesResponse.class);

            CHTChangesResponse body = response.getBody();
            int resultCount = (body != null && body.getResults() != null) ? body.getResults().size() : 0;
            logger.info("Fetched {} changes from CHT, last_seq={}", resultCount,
                body != null ? body.getLastSeq() : "null");
            return body;

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error fetching changes from CHT: status={}", e.getStatusCode());
            throw new MediatorException(
                "Failed to fetch changes from CHT: " + e.getMessage(),
                e, COMPONENT, "GET_CHANGES", e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error fetching changes from CHT: {}", e.getMessage(), e);
            throw new MediatorException(
                "Unexpected error fetching changes from CHT: " + e.getMessage(),
                e, COMPONENT, "GET_CHANGES", 500);
        }
    }

    /**
     * Test the connection to the CHT API.
     * GET /api/v1/health
     */
    public boolean testConnection() {
        try {
            String url = baseUrl + "/api/v1/health";
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("CHT connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        return headers;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCouchdbUrl() {
        return couchdbUrl;
    }
}
