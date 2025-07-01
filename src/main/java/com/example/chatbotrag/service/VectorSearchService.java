package com.example.chatbotrag.service;

import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.repository.ChunkRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VectorSearchService {

    private final ChromaHttpClientService chromaHttpClientService;
    private final OllamaClientService ollamaClient;
    private final ChunkRepository chunkRepository;

    public VectorSearchService(ChromaHttpClientService chromaHttpClientService,
                               OllamaClientService ollamaClient,
                               ChunkRepository chunkRepository) {
        this.chromaHttpClientService = chromaHttpClientService;
        this.ollamaClient = ollamaClient;
        this.chunkRepository = chunkRepository;
    }

    public List<String> findTopKRelevantChunks(String userQuestion, int topK) {
        long startTotal = System.currentTimeMillis();
        System.out.println("[VECTOR_SEARCH] 🔍 Question utilisateur : " + userQuestion);

        try {
            // 1. 🧠 Génération de l'embedding pour la question utilisateur
            System.out.println("[VECTOR_SEARCH] 📊 Génération de l'embedding pour la question...");
            List<Double> questionVector = ollamaClient.embed(userQuestion);
            
            if (questionVector == null || questionVector.isEmpty()) {
                System.err.println("[VECTOR_SEARCH] ❌ Impossible de générer l'embedding pour la question. Fallback vers recherche par mots-clés.");
                return fallbackKeywordSearch(userQuestion, topK);
            }
            
            System.out.println("[VECTOR_SEARCH] ✅ Embedding généré (dimension: " + questionVector.size() + ")");
            
            // 2. 🎯 Conversion en format float array
            float[] questionEmbedding = new float[questionVector.size()];
            for (int i = 0; i < questionVector.size(); i++) {
                questionEmbedding[i] = questionVector.get(i).floatValue();
            }
            
            // 3. 🔎 Recherche vectorielle dans ChromaDB avec HTTP client
            System.out.println("[VECTOR_SEARCH] 🔎 Recherche vectorielle dans emsi-ai-collection...");
            List<ChromaHttpClientService.SearchResult> searchResults = chromaHttpClientService.searchSimilar(
                    "emsi-ai-collection", 
                    questionEmbedding, 
                    topK * 3, // Récupérer encore plus de résultats pour filtrage
                    0.0 // Score de similarité minimum (accepter tous les résultats)
            );
            
            // Debug: Log all search results
            System.out.println("[VECTOR_SEARCH] 📊 Tous les résultats trouvés :");
            for (int i = 0; i < searchResults.size(); i++) {
                ChromaHttpClientService.SearchResult result = searchResults.get(i);
                String preview = result.getText().substring(0, Math.min(150, result.getText().length())).replace("\n", " ");
                System.out.println("[VECTOR_SEARCH] Result " + i + " (score: " + String.format("%.4f", result.getScore()) + "): " + preview + "...");
                
                // Check if this result contains our target content
                if (result.getText().contains("0101292000") || result.getText().contains("de course")) {
                    System.out.println("[VECTOR_SEARCH] ✅ FOUND TARGET CONTENT in result " + i);
                }
            }
            
            if (searchResults.isEmpty()) {
                System.err.println("[VECTOR_SEARCH] ⚠️ Aucun résultat vectoriel trouvé. Fallback vers recherche par mots-clés.");
                return fallbackKeywordSearch(userQuestion, topK);
            }
            
            // 4. 📋 Extraction et post-filtrage des résultats
            List<String> results = new ArrayList<>();
            System.out.println("[VECTOR_SEARCH] 📋 Résultats trouvés : " + searchResults.size());
            
            for (ChromaHttpClientService.SearchResult searchResult : searchResults) {
                if (results.size() >= topK) break;
                
                String chunkText = searchResult.getText();
                double score = searchResult.getScore();
                String chunkId = searchResult.getId();
                
                // 5. ✅ Filtrage intelligent basé sur le contenu
                if (isValidResult(chunkText, userQuestion) || containsRelevantContent(chunkText, userQuestion)) {
                    results.add(chunkText);
                    System.out.println("[VECTOR_SEARCH] ✅ Chunk sélectionné (ID: " + chunkId + ", score: " + String.format("%.4f", score) + "): " 
                        + chunkText.substring(0, Math.min(100, chunkText.length())).replace("\n", " ") + "...");
                } else {
                    System.out.println("[VECTOR_SEARCH] ❌ Chunk rejeté (ID: " + chunkId + ", score: " + String.format("%.4f", score) + "): " 
                        + chunkText.substring(0, Math.min(100, chunkText.length())).replace("\n", " ") + "...");
                }
            }
            
            long duration = System.currentTimeMillis() - startTotal;
            System.out.println("[VECTOR_SEARCH] ⏱️ Recherche vectorielle terminée en " + duration + "ms. Chunks retournés : " + results.size());
            
            return results.isEmpty() ? fallbackKeywordSearch(userQuestion, topK) : results;
            
        } catch (Exception e) {
            System.err.println("[VECTOR_SEARCH] ❌ Erreur lors de la recherche vectorielle : " + e.getMessage());
            e.printStackTrace();
            return fallbackKeywordSearch(userQuestion, topK);
        }
    }
    
    /**
     * 🔄 Méthode de fallback utilisant la recherche par mots-clés (ancienne méthode)
     */
    private List<String> fallbackKeywordSearch(String userQuestion, int topK) {
        System.out.println("[FALLBACK] 🔄 Utilisation de la recherche par mots-clés comme fallback");
        
        int chunkLimit = 500;
        List<Chunk> allChunks = chunkRepository.findTopN(chunkLimit);
        
        // Extraction des mots-clés
        String[] stopwords = {"le","la","les","un","une","de","du","des","pour","avec","et","en","au","aux","je","tu","il","elle","on","nous","vous","ils","elles","mon","ma","mes","ton","ta","tes","son","sa","ses","notre","nos","votre","vos","leur","leurs","ce","cet","cette","ces","dans","par","sur","à","est","suis","es","sommes","êtes","sont","se","sa","ses","seulement","ne","pas","plus","ou","où","que","qui","quoi","quand","comment","combien","donc","car","si","mais","or","ni","donc","alors","lui","leur","y","en"};
        Set<String> stopSet = new HashSet<>(Arrays.asList(stopwords));
        String[] tokens = userQuestion.toLowerCase().replaceAll("[^a-zA-Z0-9éèêàùçôûîïöü' ]", " ").split("\\s+");
        Set<String> keywords = new HashSet<>();
        for (String token : tokens) {
            if (!stopSet.contains(token) && token.length() > 2) keywords.add(token);
        }
        
        // Scoring par mots-clés
        List<ScoredChunk> scored = new ArrayList<>();
        for (Chunk chunk : allChunks) {
            if (chunk == null || chunk.getText() == null || chunk.getText().isBlank()) continue;
            String chunkText = chunk.getText();
            String chunkLower = chunkText.toLowerCase();
            
            int score = 0;
            for (String kw : keywords) {
                if (chunkLower.contains(kw)) score++;
            }
            
            if (score > 0) {
                scored.add(new ScoredChunk(chunkText, score, false, false));
            }
        }
        
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        
        List<String> result = new ArrayList<>();
        for (ScoredChunk sc : scored) {
            if (result.size() >= topK) break;
            result.add(sc.chunk);
        }
        
        System.out.println("[FALLBACK] 📋 Chunks sélectionnés par mots-clés : " + result.size());
        return result;
    }
    
    /**
     * 🔍 Validation des résultats (peut être personnalisée selon vos besoins)
     */
    private boolean isValidResult(String chunkText, String userQuestion) {
        if (chunkText == null || chunkText.trim().isEmpty()) {
            return false;
        }
        
        // Accepter tous les résultats par défaut (la recherche vectorielle fait déjà le filtrage)
        return true;
    }
    
    /**
     * 🎯 Vérification de contenu spécifiquement pertinent
     */
    private boolean containsRelevantContent(String chunkText, String userQuestion) {
        if (chunkText == null || chunkText.trim().isEmpty()) {
            return false;
        }
        
        String chunkLower = chunkText.toLowerCase();
        String questionLower = userQuestion.toLowerCase();
        
        // Recherche de codes SH spécifiques
        if (questionLower.contains("cheval") || questionLower.contains("course")) {
            if (chunkText.contains("0101292000") || 
                (chunkLower.contains("cheval") && chunkLower.contains("course")) ||
                (chunkLower.contains("âgés de moins de six") && chunkLower.contains("vingt"))) {
                System.out.println("[CONTENT_MATCH] 🎯 Trouvé contenu spécifique pour chevaux de course");
                return true;
            }
        }
        
        // Recherche générale de codes SH
        if (chunkText.matches(".*\\b\\d{10}\\b.*")) {
            System.out.println("[CONTENT_MATCH] 🎯 Trouvé code SH dans le chunk");
            return true;
        }
        
        return false;
    }

    static class ScoredChunk {
        String chunk;
        int score;
        boolean hasSH;
        boolean incoherent;
        ScoredChunk(String c, int s, boolean h, boolean i) { chunk = c; score = s; hasSH = h; incoherent = i; }
    }
}
