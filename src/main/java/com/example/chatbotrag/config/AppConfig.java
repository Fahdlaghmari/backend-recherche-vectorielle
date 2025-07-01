package com.example.chatbotrag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${ollama.baseurl:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embeddingmodel.name:nomic-embed-text}")
    private String ollamaEmbeddingModelName;

    @Value("${ollama.timeout.read:120}")
    private int ollamaReadTimeout;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModelName)
                .timeout(Duration.ofSeconds(ollamaReadTimeout))
                .build();
    }
}