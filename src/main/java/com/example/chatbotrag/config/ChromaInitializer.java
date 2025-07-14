package com.example.chatbotrag.config;

import com.example.chatbotrag.service.ChromaHttpClientService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChromaInitializer {

    @Bean
    public ApplicationRunner initializeChromaCollection(ChromaHttpClientService chromaHttpClientService) {
        return args -> {
            System.out.println("🚀 [STARTUP] Initialisation de la collection ChromaDB...");
            try {
                chromaHttpClientService.createCollectionIfNotExists(Constants.CHROMA_COLLECTION_NAME);
                System.out.println("✅ [STARTUP] Collection ChromaDB initialisée avec succès");
            } catch (Exception e) {
                System.err.println("❌ [STARTUP] Erreur lors de l'initialisation de la collection ChromaDB: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
