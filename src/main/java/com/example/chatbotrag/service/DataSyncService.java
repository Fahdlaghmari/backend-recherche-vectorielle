package com.example.chatbotrag.service;

import com.example.chatbotrag.config.Constants;
import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.model.Document;
import com.example.chatbotrag.repository.ChunkRepository;
import com.example.chatbotrag.repository.DocumentRepository;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DataSyncService {

    private final ChromaEmbeddingStore embeddingStore;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ChromaHttpClientService chromaHttpClientService;

    public DataSyncService(
            ChromaEmbeddingStore embeddingStore,
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            ChromaHttpClientService chromaHttpClientService) {
        this.embeddingStore = embeddingStore;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chromaHttpClientService = chromaHttpClientService;
    }

    /**
     * Synchronizes data from ChromaDB to MySQL database
     * This method recovers chunks that exist in ChromaDB but are missing from MySQL
     */
    @Transactional
    public String syncChromaToMySQL() {
        try {
            System.out.println("[SYNC] Starting ChromaDB to MySQL synchronization...");
            
            // Get all chunks from ChromaDB (using the correct collection name)
            List<String> chromaChunkIds = chromaHttpClientService.getAllChunkIds(Constants.CHROMA_COLLECTION_NAME);
            System.out.println("[SYNC] Found " + chromaChunkIds.size() + " chunks in ChromaDB");
            
            if (chromaChunkIds.isEmpty()) {
                return "No chunks found in ChromaDB to synchronize";
            }

            // Check which chunks are missing from MySQL
            List<String> existingChunkIds = chunkRepository.findAll().stream()
                    .map(Chunk::getId)
                    .toList();
            
            List<String> missingChunkIds = chromaChunkIds.stream()
                    .filter(id -> !existingChunkIds.contains(id))
                    .toList();
            
            System.out.println("[SYNC] Found " + missingChunkIds.size() + " chunks missing from MySQL");
            
            if (missingChunkIds.isEmpty()) {
                return "All chunks are already synchronized";
            }

            // Create a generic document for orphaned chunks
            Document genericDocument = new Document();
            genericDocument.setName("Recovered_Document_" + UUID.randomUUID().toString().substring(0, 8));
            genericDocument.setLanguage("unknown");
            genericDocument.setChunks(new ArrayList<>());
            
            Document savedDocument = documentRepository.save(genericDocument);
            System.out.println("[SYNC] Created generic document: " + savedDocument.getName() + " (ID: " + savedDocument.getId() + ")");

            // Recover chunks from ChromaDB
            int recoveredCount = 0;
            for (String chunkId : missingChunkIds) {
                try {
                    String chunkText = chromaHttpClientService.getChunkText(chunkId, Constants.CHROMA_COLLECTION_NAME);
                    if (chunkText != null && !chunkText.trim().isEmpty()) {
                        Chunk chunk = new Chunk();
                        chunk.setId(chunkId);
                        chunk.setText(chunkText);
                        chunk.setDocument(savedDocument);
                        
                        chunkRepository.save(chunk);
                        savedDocument.getChunks().add(chunk);
                        recoveredCount++;
                        
                        System.out.println("[SYNC] Recovered chunk: " + chunkId.substring(0, Math.min(20, chunkId.length())) + "...");
                    }
                } catch (Exception e) {
                    System.err.println("[SYNC] Failed to recover chunk " + chunkId + ": " + e.getMessage());
                }
            }

            documentRepository.save(savedDocument);
            
            String result = "Synchronization completed! Recovered " + recoveredCount + " chunks from ChromaDB to MySQL";
            System.out.println("[SYNC] " + result);
            return result;
            
        } catch (Exception e) {
            String error = "Synchronization failed: " + e.getMessage();
            System.err.println("[SYNC] " + error);
            e.printStackTrace();
            return error;
        }
    }

    /**
     * Clears all data from both ChromaDB and MySQL
     */
    @Transactional
    public String clearAllData() {
        try {
            System.out.println("[SYNC] Clearing all data from both stores...");
            
            // Clear MySQL
            chunkRepository.deleteAll();
            documentRepository.deleteAll();
            
            // Clear ChromaDB
            chromaHttpClientService.clearCollection(Constants.CHROMA_COLLECTION_NAME);
            
            String result = "All data cleared from both ChromaDB and MySQL";
            System.out.println("[SYNC] " + result);
            return result;
            
        } catch (Exception e) {
            String error = "Failed to clear data: " + e.getMessage();
            System.err.println("[SYNC] " + error);
            return error;
        }
    }

    /**
     * Gets synchronization status
     */
    public String getSyncStatus() {
        try {
            long mysqlChunkCount = chunkRepository.count();
            long mysqlDocumentCount = documentRepository.count();
            
            List<String> chromaChunkIds = chromaHttpClientService.getAllChunkIds(Constants.CHROMA_COLLECTION_NAME);
            long chromaChunkCount = chromaChunkIds.size();
            
            return String.format(
                "Sync Status:\n" +
                "MySQL - Documents: %d, Chunks: %d\n" +
                "ChromaDB - Chunks: %d\n" +
                "Sync Status: %s",
                mysqlDocumentCount,
                mysqlChunkCount,
                chromaChunkCount,
                (mysqlChunkCount == chromaChunkCount) ? "SYNCHRONIZED" : "OUT OF SYNC"
            );
        } catch (Exception e) {
            return "Failed to get sync status: " + e.getMessage();
        }
    }
}
