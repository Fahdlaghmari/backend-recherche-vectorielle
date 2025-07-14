package com.example.chatbotrag.controller;

import com.example.chatbotrag.config.Constants;
import com.example.chatbotrag.service.ChromaHttpClientService;
import com.example.chatbotrag.service.DataSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sync")
public class SyncController {

    private final DataSyncService dataSyncService;
    private final ChromaHttpClientService chromaHttpClientService;

    public SyncController(DataSyncService dataSyncService, ChromaHttpClientService chromaHttpClientService) {
        this.dataSyncService = dataSyncService;
        this.chromaHttpClientService = chromaHttpClientService;
    }

    /**
     * Synchronizes data from ChromaDB to MySQL
     */
    @PostMapping("/chroma-to-mysql")
    public ResponseEntity<String> syncChromaToMySQL() {
        try {
            String result = dataSyncService.syncChromaToMySQL();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Synchronization failed: " + e.getMessage());
        }
    }

    /**
     * Gets synchronization status
     */
    @GetMapping("/status")
    public ResponseEntity<String> getSyncStatus() {
        try {
            String status = dataSyncService.getSyncStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Failed to get sync status: " + e.getMessage());
        }
    }

    /**
     * Clears all data from both stores (USE WITH CAUTION!)
     */
    @PostMapping("/clear-all")
    public ResponseEntity<String> clearAllData() {
        try {
            String result = dataSyncService.clearAllData();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Failed to clear data: " + e.getMessage());
        }
    }

    /**
     * Gets all collection names
     */
    @GetMapping("/collections")
    public ResponseEntity<List<String>> getAllCollections() {
        try {
            List<String> collections = chromaHttpClientService.getAllCollectionNames();
            return ResponseEntity.ok(collections);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Deletes a specific collection (USE WITH CAUTION!)
     */
    @DeleteMapping("/collections/{collectionName}")
    public ResponseEntity<String> deleteCollection(@PathVariable String collectionName) {
        try {
            chromaHttpClientService.deleteCollection(collectionName);
            return ResponseEntity.ok("Collection " + collectionName + " deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Failed to delete collection: " + e.getMessage());
        }
    }

    /**
     * Standardizes to use only the configured collection and removes others
     */
    @PostMapping("/standardize-collections")
    public ResponseEntity<String> standardizeCollections() {
        try {
            List<String> allCollections = chromaHttpClientService.getAllCollectionNames();
            StringBuilder result = new StringBuilder("Collection standardization results:\n");
            
            // Ensure the configured collection exists
            if (!allCollections.contains(Constants.CHROMA_COLLECTION_NAME)) {
                chromaHttpClientService.createCollectionIfNotExists(Constants.CHROMA_COLLECTION_NAME);
                result.append("✅ Created " + Constants.CHROMA_COLLECTION_NAME + "\n");
            } else {
                result.append("✅ " + Constants.CHROMA_COLLECTION_NAME + " already exists\n");
            }
            
            // Delete other collections
            for (String collection : allCollections) {
                if (!collection.equals(Constants.CHROMA_COLLECTION_NAME)) {
                    chromaHttpClientService.deleteCollection(collection);
                    result.append("🗑️ Deleted collection: ").append(collection).append("\n");
                }
            }
            
            // Clear the configured collection to ensure it's empty
            chromaHttpClientService.clearCollection(Constants.CHROMA_COLLECTION_NAME);
            result.append("🧹 Cleared " + Constants.CHROMA_COLLECTION_NAME + "\n");
            
            // Clear MySQL data
            dataSyncService.clearAllData();
            result.append("🧹 Cleared MySQL data\n");
            
            result.append("\n✅ Standardization complete! Only " + Constants.CHROMA_COLLECTION_NAME + " remains and is empty.");
            
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Failed to standardize collections: " + e.getMessage());
        }
    }
}
