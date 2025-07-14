package com.example.chatbotrag.service;

import com.example.chatbotrag.config.Constants;
import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.model.Document;
import com.example.chatbotrag.repository.ChunkRepository;
import com.example.chatbotrag.repository.DocumentRepository;
import com.example.chatbotrag.repository.ProductMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeleteService {

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final ChromaHttpClientService chromaHttpClientService;
    private final ProductMetadataRepository productMetadataRepository;
    private static final String COLLECTION_NAME = Constants.CHROMA_COLLECTION_NAME;

    public DeleteService(ChunkRepository chunkRepository,
                         DocumentRepository documentRepository,
                         ChromaHttpClientService chromaHttpClientService,
                         ProductMetadataRepository productMetadataRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.chromaHttpClientService = chromaHttpClientService;
        this.productMetadataRepository = productMetadataRepository;
    }

    // ✅ Supprime un chunk + son embedding
    public void deleteChunkById(String chunkId) {
        // Supprimer les métadonnées avant le chunk pour éviter des erreurs de contrainte.
        productMetadataRepository.findByChunkId(chunkId).ifPresent(productMetadataRepository::delete);
        chromaHttpClientService.deleteEmbeddingById(COLLECTION_NAME, chunkId);
        chunkRepository.deleteById(chunkId);
        System.out.println("❌ Chunk supprimé : " + chunkId);
    }

    // ✅ Supprime un document + ses chunks + embeddings
    public void deleteDocumentById(String documentIdStr) {
        try {
            Long documentId = Long.parseLong(documentIdStr);
            documentRepository.findById(documentId).ifPresentOrElse(document -> {
                List<String> chunkIds = document.getChunks().stream()
                        .map(Chunk::getId)
                        .collect(Collectors.toList());

                try {
                    if (!chunkIds.isEmpty()) {
                        // Supprimer les métadonnées associées avant.
                        chunkIds.forEach(id -> productMetadataRepository.findByChunkId(id).ifPresent(productMetadataRepository::delete));
                        chromaHttpClientService.deleteEmbeddingsByIds(COLLECTION_NAME, chunkIds);
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Failed to delete embeddings from ChromaDB: " + e.getMessage());
                    System.out.println("[INFO] Continuing with database cleanup...");
                }
                documentRepository.delete(document); // cascade = chunks supprimés
                System.out.println("🧼 Document supprimé avec " + chunkIds.size() + " chunks : " + documentId);
            }, () -> {
                System.out.println("⚠️ Document introuvable : " + documentIdStr);
            });
        } catch (NumberFormatException e) {
            System.err.println("❌ ID de document invalide : " + documentIdStr);
        }
    }
}
