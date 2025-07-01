package com.example.chatbotrag.config;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChromaConfig {

    @Bean
    public ChromaEmbeddingStore embeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .collectionName("emsi-ai-collection")
                .build();
    }
}
