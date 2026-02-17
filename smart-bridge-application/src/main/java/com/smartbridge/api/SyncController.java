package com.smartbridge.api;

import com.smartbridge.core.sync.BulkSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private static final Logger logger = LoggerFactory.getLogger(SyncController.class);
    
    private final BulkSyncService bulkSyncService;
    
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
}
