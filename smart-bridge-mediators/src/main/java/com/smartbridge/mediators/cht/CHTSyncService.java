package com.smartbridge.mediators.cht;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTChangesResponse;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Syncs CHT data to FHIR using CouchDB _changes feed for incremental sync
 * or REST API pagination as fallback.
 *
 * Persists the opaque since_seq token to data/cht-since-seq.txt between runs.
 * Routes documents by type: "person" contacts, "clinic"/"health_center"/"district_hospital" places,
 * and "data_record" reports. Also supports legacy "contact" + "contact_type" pattern.
 */
@Service
public class CHTSyncService {

    private static final Logger logger = LoggerFactory.getLogger(CHTSyncService.class);
    private static final String DATA_DIR = "data";
    private static final String SINCE_SEQ_FILE = DATA_DIR + "/cht-since-seq.txt";

    // CHT place contact types
    private static final Set<String> PLACE_CONTACT_TYPES = Set.of(
        "district_hospital", "health_center", "clinic"
    );

    private final CHTApiClient chtApiClient;
    private final CHTIngestionFlowService ingestionFlowService;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final boolean useChangesFeed;
    private final boolean syncEnabled;

    public CHTSyncService(
            CHTApiClient chtApiClient,
            CHTIngestionFlowService ingestionFlowService,
            @Value("${smartbridge.cht.sync.batch-size:100}") int batchSize,
            @Value("${smartbridge.cht.sync.use-changes-feed:true}") boolean useChangesFeed,
            @Value("${smartbridge.cht.sync.enabled:true}") boolean syncEnabled) {
        this.chtApiClient = chtApiClient;
        this.ingestionFlowService = ingestionFlowService;
        this.batchSize = batchSize;
        this.useChangesFeed = useChangesFeed;
        this.syncEnabled = syncEnabled;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        ensureDataDirectory();
        logger.info("CHTSyncService initialized: enabled={}, useChangesFeed={}, batchSize={}",
            syncEnabled, useChangesFeed, batchSize);
    }

    // ==================== Public Sync Methods ====================

    /**
     * Incremental sync using persisted since_seq token.
     * Runs on a configurable schedule when enabled.
     */
    @Scheduled(fixedDelayString = "${smartbridge.cht.sync.interval-ms:300000}",
               initialDelayString = "${smartbridge.cht.sync.initial-delay-ms:30000}")
    public void incrementalSync() {
        if (!syncEnabled) {
            logger.debug("CHT sync is disabled, skipping incremental sync");
            return;
        }

        logger.info("Starting incremental CHT sync");
        String sinceSeq = loadSinceSeq();

        if (useChangesFeed) {
            syncFromChanges(sinceSeq);
        } else {
            syncFromRestApi();
        }
    }

    /**
     * Full bulk sync — resets the since_seq to "0" and re-processes all documents.
     */
    public void bulkSync() {
        logger.info("Starting full bulk CHT sync from scratch");
        saveSinceSeq("0");
        ingestionFlowService.clearDeduplicationCache();

        if (useChangesFeed) {
            syncFromChanges("0");
        } else {
            syncFromRestApi();
        }
    }

    // ==================== Changes Feed Sync ====================

    private void syncFromChanges(String fromSeq) {
        int totalSuccess = 0;
        int totalErrors = 0;
        String currentSeq = fromSeq;
        boolean hasMore = true;

        try {
            while (hasMore) {
                logger.info("Fetching CHT changes: since={}, limit={}", currentSeq, batchSize);

                CHTChangesResponse response = chtApiClient.getChanges(currentSeq, batchSize);

                if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                    logger.info("No more changes to process");
                    break;
                }

                logger.info("Received {} changes from CHT", response.getResults().size());

                for (CHTChangesResponse.CHTChangeResult change : response.getResults()) {
                    if (change.isDeleted()) {
                        handleDeletion(change);
                        continue;
                    }

                    Map<String, Object> doc = change.getDoc();
                    if (doc == null) {
                        continue;
                    }

                    try {
                        routeDocument(doc);
                        totalSuccess++;
                    } catch (Exception e) {
                        logger.error("Failed to process CHT document: id={}", change.getId(), e);
                        totalErrors++;
                    }
                }

                // Persist last_seq after processing each batch
                if (response.getLastSeq() != null) {
                    saveSinceSeq(response.getLastSeq());
                    currentSeq = response.getLastSeq();
                }

                // Stop when fewer results than batch size or no pending
                if (response.getResults().size() < batchSize) {
                    hasMore = false;
                } else if (response.getPending() != null && response.getPending() == 0) {
                    hasMore = false;
                }
            }

            logger.info("CHT changes sync complete: {} success, {} errors, lastSeq={}",
                totalSuccess, totalErrors, currentSeq);

        } catch (Exception e) {
            logger.error("CHT changes sync failed", e);
        }
    }

    // ==================== REST API Fallback ====================

    private void syncFromRestApi() {
        int totalSuccess = 0;
        int totalErrors = 0;
        int skip = 0;
        boolean hasMore = true;

        try {
            // Sync persons via REST API pagination
            logger.info("Syncing persons via CHT REST API");
            while (hasMore) {
                List<CHTContact> persons = chtApiClient.listPersons(batchSize, skip);

                if (persons == null || persons.isEmpty()) {
                    break;
                }

                for (CHTContact person : persons) {
                    try {
                        ingestionFlowService.processContactIngestion(person);
                        totalSuccess++;
                    } catch (Exception e) {
                        logger.error("Failed to process CHT person: id={}", person.getId(), e);
                        totalErrors++;
                    }
                }

                if (persons.size() < batchSize) {
                    hasMore = false;
                } else {
                    skip += batchSize;
                }
            }

            logger.info("CHT REST API sync complete: {} success, {} errors",
                totalSuccess, totalErrors);

        } catch (Exception e) {
            logger.error("CHT REST API sync failed", e);
        }
    }

    // ==================== Document Routing ====================

    private void routeDocument(Map<String, Object> doc) throws Exception {
        String type = (String) doc.get("type");

        if ("person".equals(type)) {
            // Real CHT documents use type: "person" directly
            CHTContact contact = objectMapper.convertValue(doc, CHTContact.class);
            ingestionFlowService.processContactIngestion(contact);
        } else if (PLACE_CONTACT_TYPES.contains(type)) {
            // Real CHT documents use type: "clinic"/"health_center"/"district_hospital" directly
            CHTPlace place = objectMapper.convertValue(doc, CHTPlace.class);
            ingestionFlowService.processPlaceIngestion(place);
        } else if ("contact".equals(type)) {
            // Legacy/configurable CHT uses type: "contact" + contact_type
            String contactType = (String) doc.get("contact_type");
            if ("person".equals(contactType)) {
                CHTContact contact = objectMapper.convertValue(doc, CHTContact.class);
                ingestionFlowService.processContactIngestion(contact);
            } else if (contactType != null && PLACE_CONTACT_TYPES.contains(contactType)) {
                CHTPlace place = objectMapper.convertValue(doc, CHTPlace.class);
                ingestionFlowService.processPlaceIngestion(place);
            } else {
                logger.debug("Skipping contact with unknown contact_type: {}", contactType);
            }
        } else if ("data_record".equals(type)) {
            CHTReport report = objectMapper.convertValue(doc, CHTReport.class);
            ingestionFlowService.processReportIngestion(report);
        } else {
            logger.debug("Skipping document with unhandled type: {}", type);
        }
    }

    private void handleDeletion(CHTChangesResponse.CHTChangeResult change) {
        logger.info("Document deleted in CHT: id={}", change.getId());
        // Future: mark corresponding FHIR resource as inactive
    }

    // ==================== Since Seq Persistence ====================

    public String loadSinceSeq() {
        try {
            Path path = Paths.get(SINCE_SEQ_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                if (!content.isEmpty()) {
                    return content;
                }
            }
        } catch (IOException e) {
            logger.warn("Could not load CHT since_seq, starting from 0", e);
        }
        return "0";
    }

    void saveSinceSeq(String sinceSeq) {
        try {
            Path path = Paths.get(SINCE_SEQ_FILE);
            Files.writeString(path, sinceSeq);
            logger.debug("Saved CHT since_seq: {}", sinceSeq);
        } catch (IOException e) {
            logger.error("Failed to save CHT since_seq", e);
        }
    }

    private void ensureDataDirectory() {
        try {
            Path dir = Paths.get(DATA_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }
}
