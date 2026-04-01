package com.smartbridge.api;

import com.smartbridge.core.sync.BulkSyncService;
import com.smartbridge.mediators.cht.CHTSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private static final Logger logger = LoggerFactory.getLogger(SyncController.class);

    private final BulkSyncService bulkSyncService;

    @Autowired(required = false)
    private CHTSyncService chtSyncService;

    public SyncController(BulkSyncService bulkSyncService) {
        this.bulkSyncService = bulkSyncService;
    }

    @PostMapping("/bulk")
    public ResponseEntity<String> triggerBulkSync() {
        logger.info("Manual bulk sync triggered");
        bulkSyncService.bulkSync();
        return ResponseEntity.ok("Bulk sync started");
    }

    @PostMapping("/incremental")
    public ResponseEntity<String> triggerIncrementalSync() {
        logger.info("Manual incremental sync triggered");
        bulkSyncService.incrementalSync();
        return ResponseEntity.ok("Incremental sync started");
    }

    // ==================== CHT Sync Endpoints ====================

    @PostMapping("/cht/bulk")
    public ResponseEntity<String> triggerCHTBulkSync() {
        if (chtSyncService == null) {
            return ResponseEntity.badRequest().body("CHT sync service not configured");
        }
        logger.info("Manual CHT bulk sync triggered");
        chtSyncService.bulkSync();
        return ResponseEntity.ok("CHT bulk sync started");
    }

    @PostMapping("/cht/incremental")
    public ResponseEntity<String> triggerCHTIncrementalSync() {
        if (chtSyncService == null) {
            return ResponseEntity.badRequest().body("CHT sync service not configured");
        }
        logger.info("Manual CHT incremental sync triggered");
        chtSyncService.incrementalSync();
        return ResponseEntity.ok("CHT incremental sync started");
    }

    @GetMapping("/cht/status")
    public ResponseEntity<Map<String, Object>> getCHTSyncStatus() {
        if (chtSyncService == null) {
            return ResponseEntity.badRequest().build();
        }
        String sinceSeq = chtSyncService.loadSinceSeq();
        Map<String, Object> status = Map.of(
            "since_seq", sinceSeq,
            "enabled", true
        );
        return ResponseEntity.ok(status);
    }
}
