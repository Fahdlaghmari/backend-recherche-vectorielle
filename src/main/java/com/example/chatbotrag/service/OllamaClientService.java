package com.example.chatbotrag.service;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaClientService {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "http://localhost:11434"; // L'URL de base d'Ollama

    public OllamaClientService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }


    public List<Double> embed(String input) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "nomic-embed-text"); // Modèle d'embedding
        body.put("prompt", input);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = restTemplate.postForObject(BASE_URL + "/api/embeddings", body, Map.class);

            if (responseMap != null && responseMap.get("embedding") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Double> vector = (List<Double>) responseMap.get("embedding");
                return vector;
            } else {
                System.err.println("Ollama embed response: corps de réponse nul ou 'embedding' n'est pas une liste. Réponse: " + responseMap);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'appel à Ollama pour l'embedding: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 🤖 Génération de réponse avec Mistral (mode non-streaming)
     */
    public String generateWithMistral(String prompt) {
        return generateResponse(prompt, "mistral", false);
    }
    
    /**
     * 🌊 Génération de réponse avec Mistral (mode streaming)
     */
    public List<String> streamWithMistral(String prompt) {
        return generateStreamResponse(prompt, "mistral");
    }
    
    /**
     * 📝 Génération de réponse avec un modèle spécifique (mode non-streaming)
     */
    public String generateResponse(String prompt, String model, boolean stream) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", model);
        requestBodyMap.put("prompt", prompt);
        requestBodyMap.put("stream", false);

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2); // Température basse pour des réponses factuelles
        options.put("top_p", 0.9);
        options.put("top_k", 40);
        requestBodyMap.put("options", options);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = restTemplate.postForObject(BASE_URL + "/api/generate", requestBodyMap, Map.class);

            if (responseMap != null && responseMap.get("response") instanceof String) {
                return (String) responseMap.get("response");
            } else {
                System.err.println("[MISTRAL] Réponse invalide: " + responseMap);
                return "Erreur lors de la génération de la réponse.";
            }
        } catch (Exception e) {
            System.err.println("[MISTRAL] Erreur lors de l'appel à Ollama avec " + model + ": " + e.getMessage());
            e.printStackTrace();
            return "Erreur lors de la communication avec le modèle " + model + ".";
        }
    }
    
    /**
     * 🌊 Génération de réponse en streaming avec un modèle spécifique
     */
    public List<String> generateStreamResponse(String prompt, String model) {
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", model);
        requestBodyMap.put("prompt", prompt);
        requestBodyMap.put("stream", true);

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2);
        options.put("top_p", 0.9);
        options.put("top_k", 40);
        requestBodyMap.put("options", options);

        try {
            List<String> responses = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chunkMaps = restTemplate.postForObject(BASE_URL + "/api/generate", requestBodyMap, List.class);

            if (chunkMaps != null) {
                for (Map<String, Object> chunkMap : chunkMaps) {
                    Object responseObj = chunkMap.get("response");
                    if (responseObj instanceof String) {
                        responses.add((String) responseObj);
                    }
                }
            }
            return responses;
        } catch (Exception e) {
            System.err.println("[MISTRAL] Erreur lors du streaming avec " + model + ": " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Deprecated
    public List<String> chatStream(String prompt, String model) {
        System.out.println("[DEPRECATED] Utilisation de chatStream obsolète. Utilisez generateStreamResponse() à la place.");
        return generateStreamResponse(prompt, model);
    }
}