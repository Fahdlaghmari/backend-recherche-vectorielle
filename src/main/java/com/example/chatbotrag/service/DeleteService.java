package com.example.chatbotrag.service;

import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.model.Document;
import com.example.chatbotrag.repository.ChunkRepository;
import com.example.chatbotrag.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeleteService {

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final ChromaHttpClientService chromaHttpClientService;
    private static final String COLLECTION_NAME = "default";

    public DeleteService(ChunkRepository chunkRepository,
                         DocumentRepository documentRepository,
                         ChromaHttpClientService chromaHttpClientService) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.chromaHttpClientService = chromaHttpClientService;
    }

    // ✅ Supprime un chunk + son embedding
    public void deleteChunkById(String chunkId) {
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

                chromaHttpClientService.deleteEmbeddingsByIds(COLLECTION_NAME, chunkIds);
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
