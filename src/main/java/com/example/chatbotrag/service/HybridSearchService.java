package com.example.chatbotrag.service;

import com.example.chatbotrag.model.ProductMetadata;
import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.repository.ProductMetadataRepository;
import com.example.chatbotrag.repository.ChunkRepository;
import com.example.chatbotrag.config.Constants;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de recherche hybride qui combine :
 * 1. Recherche vectorielle classique
 * 2. Filtrage par métadonnées structurées
 * 3. Scoring hybride (vectoriel + métadonnées)
 */
@Service
public class HybridSearchService {

    private final VectorSearchService vectorSearchService;
    private final ProductMetadataRepository productMetadataRepository;
    private final ProductMetadataExtractionService metadataExtractionService;
    private final ChromaHttpClientService chromaHttpClientService;
    private final OllamaClientService ollamaClient;
    private final ChunkRepository chunkRepository;

    public HybridSearchService(VectorSearchService vectorSearchService,
                               ProductMetadataRepository productMetadataRepository,
                               ProductMetadataExtractionService metadataExtractionService,
                               ChromaHttpClientService chromaHttpClientService,
                               OllamaClientService ollamaClient,
                               ChunkRepository chunkRepository) {
        this.vectorSearchService = vectorSearchService;
        this.productMetadataRepository = productMetadataRepository;
        this.metadataExtractionService = metadataExtractionService;
        this.chromaHttpClientService = chromaHttpClientService;
        this.ollamaClient = ollamaClient;
        this.chunkRepository = chunkRepository;
    }

    /**
     * Recherche hybride principale
     */
    public List<HybridSearchResult> searchHybrid(String userQuery, int topK) {
        System.out.println("🔍 [HYBRID_SEARCH] Début recherche hybride pour: " + userQuery);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Extraction des métadonnées de la requête
            Map<String, String> queryMetadata = metadataExtractionService.extractQueryMetadata(userQuery);
            System.out.println("📊 [HYBRID_SEARCH] Métadonnées extraites: " + queryMetadata);

            // 2. Recherche par métadonnées exactes (priorité haute)
            List<HybridSearchResult> metadataResults = searchByMetadata(queryMetadata, topK);
            
            // 3. Recherche vectorielle
            List<HybridSearchResult> vectorResults = performVectorSearch(userQuery, topK * 2);
            
            // 4. Fusion et scoring hybride
            List<HybridSearchResult> hybridResults = combineAndScore(metadataResults, vectorResults, queryMetadata);
            
            // 5. Tri et sélection finale
            List<HybridSearchResult> finalResults = hybridResults.stream()
                    .sorted((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()))
                    .limit(topK)
                    .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("✅ [HYBRID_SEARCH] Recherche terminée en " + duration + "ms. Résultats: " + finalResults.size());
            
            return finalResults;

        } catch (Exception e) {
            System.err.println("❌ [HYBRID_SEARCH] Erreur: " + e.getMessage());
            e.printStackTrace();
            return fallbackToVectorSearch(userQuery, topK);
        }
    }

    /**
     * Recherche basée sur les métadonnées structurées
     */
    private List<HybridSearchResult> searchByMetadata(Map<String, String> queryMetadata, int topK) {
        System.out.println("📋 [METADATA_SEARCH] Recherche par métadonnées...");
        
        List<HybridSearchResult> results = new ArrayList<>();
        
        if (queryMetadata.isEmpty()) {
            System.out.println("⚠️ [METADATA_SEARCH] Aucune métadonnée extraite");
            return results;
        }

        // Recherche multi-critères
        List<ProductMetadata> candidates = productMetadataRepository.findByMultipleCriteria(
                queryMetadata.get("type"),
                queryMetadata.get("etat"),
                queryMetadata.get("desossage"),
                queryMetadata.get("usage")
        );

        System.out.println("📊 [METADATA_SEARCH] Candidats trouvés: " + candidates.size());

        // Scoring et conversion
        for (ProductMetadata metadata : candidates) {
            if (metadata.getChunk() != null) {
                double metadataScore = metadata.calculateMetadataScore(queryMetadata);
                if (metadataScore > 0.0) {
                    HybridSearchResult result = new HybridSearchResult(
                            metadata.getChunk().getText(),
                            metadata.getChunk().getId(),
                            0.0, // Pas de score vectoriel pour l'instant
                            metadataScore,
                            metadata
                    );
                    results.add(result);
                    System.out.println("✅ [METADATA_SEARCH] Ajouté résultat SH: " + metadata.getCodeSh() + 
                                       " (score: " + String.format("%.3f", metadataScore) + ")");
                }
            }
        }

        // Recherche fuzzy si pas assez de résultats
        if (results.size() < topK) {
            results.addAll(performFuzzyMetadataSearch(queryMetadata, topK - results.size()));
        }

        return results;
    }

    /**
     * Recherche fuzzy par métadonnées
     */
    private List<HybridSearchResult> performFuzzyMetadataSearch(Map<String, String> queryMetadata, int needed) {
        System.out.println("🔍 [FUZZY_SEARCH] Recherche fuzzy...");
        
        List<HybridSearchResult> results = new ArrayList<>();
        
        // Recherche par mots-clés et synonymes
        for (Map.Entry<String, String> entry : queryMetadata.entrySet()) {
            if (results.size() >= needed) break;
            
            String value = entry.getValue();
            List<ProductMetadata> fuzzyResults = productMetadataRepository.findByKeywordsOrSynonyms(value);
            
            for (ProductMetadata metadata : fuzzyResults) {
                if (results.size() >= needed) break;
                if (metadata.getChunk() != null) {
                    double fuzzyScore = calculateFuzzyScore(metadata, queryMetadata);
                    if (fuzzyScore > 0.3) { // Seuil minimum
                        HybridSearchResult result = new HybridSearchResult(
                                metadata.getChunk().getText(),
                                metadata.getChunk().getId(),
                                0.0,
                                fuzzyScore,
                                metadata
                        );
                        results.add(result);
                        System.out.println("🔍 [FUZZY_SEARCH] Ajouté résultat fuzzy SH: " + metadata.getCodeSh());
                    }
                }
            }
        }

        return results;
    }

    /**
     * Recherche vectorielle
     */
    private List<HybridSearchResult> performVectorSearch(String userQuery, int topK) {
        System.out.println("🧠 [VECTOR_SEARCH] Recherche vectorielle...");
        
        List<HybridSearchResult> results = new ArrayList<>();
        
        try {
            // Génération embedding
            List<Double> questionVector = ollamaClient.embed(userQuery);
            if (questionVector == null || questionVector.isEmpty()) {
                System.err.println("❌ [VECTOR_SEARCH] Impossible de générer l'embedding");
                return results;
            }

            float[] questionEmbedding = new float[questionVector.size()];
            for (int i = 0; i < questionVector.size(); i++) {
                questionEmbedding[i] = questionVector.get(i).floatValue();
            }

            // Recherche dans ChromaDB
            List<ChromaHttpClientService.SearchResult> vectorResults = chromaHttpClientService.searchSimilar(
                    Constants.CHROMA_COLLECTION_NAME,
                    questionEmbedding,
                    topK,
                    0.1
            );

            System.out.println("📊 [VECTOR_SEARCH] Résultats vectoriels: " + vectorResults.size());

            // Conversion en HybridSearchResult
            for (ChromaHttpClientService.SearchResult vectorResult : vectorResults) {
                // Recherche des métadonnées associées
                Optional<ProductMetadata> metadata = productMetadataRepository.findByChunkId(vectorResult.getId());
                
                HybridSearchResult result = new HybridSearchResult(
                        vectorResult.getText(),
                        vectorResult.getId(),
                        vectorResult.getScore(),
                        0.0, // Pas de score métadonnées pour l'instant
                        metadata.orElse(null)
                );
                results.add(result);
            }

        } catch (Exception e) {
            System.err.println("❌ [VECTOR_SEARCH] Erreur: " + e.getMessage());
        }

        return results;
    }

    /**
     * Combine et score les résultats de différentes sources
     */
    private List<HybridSearchResult> combineAndScore(List<HybridSearchResult> metadataResults,
                                                     List<HybridSearchResult> vectorResults,
                                                     Map<String, String> queryMetadata) {
        System.out.println("🔗 [COMBINE_SCORE] Fusion des résultats...");
        
        Map<String, HybridSearchResult> combinedResults = new HashMap<>();
        
        // Ajout des résultats métadonnées
        for (HybridSearchResult result : metadataResults) {
            combinedResults.put(result.getChunkId(), result);
        }
        
        // Fusion avec les résultats vectoriels
        for (HybridSearchResult vectorResult : vectorResults) {
            String chunkId = vectorResult.getChunkId();
            
            if (combinedResults.containsKey(chunkId)) {
                // Mise à jour du score vectoriel
                HybridSearchResult existing = combinedResults.get(chunkId);
                existing.setVectorScore(vectorResult.getVectorScore());
                
                // Recalcul du score métadonnées si nécessaire
                if (existing.getMetadataScore() == 0.0 && existing.getMetadata() != null) {
                    existing.setMetadataScore(existing.getMetadata().calculateMetadataScore(queryMetadata));
                }
            } else {
                // Nouveau résultat vectoriel
                if (vectorResult.getMetadata() != null) {
                    vectorResult.setMetadataScore(vectorResult.getMetadata().calculateMetadataScore(queryMetadata));
                }
                combinedResults.put(chunkId, vectorResult);
            }
        }
        
        // Calcul des scores finaux
        for (HybridSearchResult result : combinedResults.values()) {
            double totalScore = calculateHybridScore(result.getVectorScore(), result.getMetadataScore());
            result.setTotalScore(totalScore);
        }
        
        System.out.println("✅ [COMBINE_SCORE] Résultats combinés: " + combinedResults.size());
        return new ArrayList<>(combinedResults.values());
    }

    /**
     * Calcul du score hybride
     */
    private double calculateHybridScore(double vectorScore, double metadataScore) {
        // Pondération : 60% vectoriel, 40% métadonnées
        double vectorWeight = 0.6;
        double metadataWeight = 0.4;
        
        // Normalisation des scores
        double normalizedVectorScore = Math.max(0.0, Math.min(1.0, vectorScore));
        double normalizedMetadataScore = Math.max(0.0, Math.min(1.0, metadataScore));
        
        // Boost si les deux scores sont élevés
        double combinedScore = (vectorWeight * normalizedVectorScore) + (metadataWeight * normalizedMetadataScore);
        
        // Bonus si les deux méthodes sont d'accord
        if (normalizedVectorScore > 0.5 && normalizedMetadataScore > 0.5) {
            combinedScore *= 1.2; // Boost de 20%
        }
        
        return Math.min(1.0, combinedScore);
    }

    /**
     * Calcul du score fuzzy
     */
    private double calculateFuzzyScore(ProductMetadata metadata, Map<String, String> queryMetadata) {
        double score = 0.0;
        int matches = 0;
        
        // Correspondances partielles
        for (Map.Entry<String, String> entry : queryMetadata.entrySet()) {
            String queryValue = entry.getValue().toLowerCase();
            String key = entry.getKey();
            
            switch (key) {
                case "type":
                    if (metadata.getTypeProduit() != null && 
                        metadata.getTypeProduit().toLowerCase().contains(queryValue)) {
                        score += 0.3;
                        matches++;
                    }
                    break;
                case "etat":
                    if (metadata.getEtatProduit() != null && 
                        metadata.getEtatProduit().toLowerCase().contains(queryValue)) {
                        score += 0.2;
                        matches++;
                    }
                    break;
                case "desossage":
                    if (metadata.getDesossage() != null && 
                        metadata.getDesossage().toLowerCase().contains(queryValue)) {
                        score += 0.2;
                        matches++;
                    }
                    break;
                case "usage":
                    if (metadata.getUsageSpecifique() != null && 
                        metadata.getUsageSpecifique().toLowerCase().contains(queryValue)) {
                        score += 0.3;
                        matches++;
                    }
                    break;
            }
        }
        
        // Bonus pour les correspondances multiples
        if (matches > 1) {
            score *= 1.5;
        }
        
        return Math.min(1.0, score);
    }

    /**
     * Fallback vers recherche vectorielle pure
     */
    private List<HybridSearchResult> fallbackToVectorSearch(String userQuery, int topK) {
        System.out.println("🔄 [FALLBACK] Utilisation de la recherche vectorielle pure");
        
        List<String> vectorResults = vectorSearchService.findTopKRelevantChunks(userQuery, topK);
        List<HybridSearchResult> results = new ArrayList<>();
        
        for (int i = 0; i < vectorResults.size(); i++) {
            String text = vectorResults.get(i);
            HybridSearchResult result = new HybridSearchResult(
                    text,
                    "fallback_" + i,
                    0.8 - (i * 0.1), // Score décroissant
                    0.0,
                    null
            );
            results.add(result);
        }
        
        return results;
    }

    /**
     * Classe pour les résultats de recherche hybride
     */
    public static class HybridSearchResult {
        private String text;
        private String chunkId;
        private double vectorScore;
        private double metadataScore;
        private double totalScore;
        private ProductMetadata metadata;

        public HybridSearchResult(String text, String chunkId, double vectorScore, 
                                  double metadataScore, ProductMetadata metadata) {
            this.text = text;
            this.chunkId = chunkId;
            this.vectorScore = vectorScore;
            this.metadataScore = metadataScore;
            this.metadata = metadata;
            this.totalScore = 0.0;
        }

        // Getters et setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public String getChunkId() { return chunkId; }
        public void setChunkId(String chunkId) { this.chunkId = chunkId; }
        
        public double getVectorScore() { return vectorScore; }
        public void setVectorScore(double vectorScore) { this.vectorScore = vectorScore; }
        
        public double getMetadataScore() { return metadataScore; }
        public void setMetadataScore(double metadataScore) { this.metadataScore = metadataScore; }
        
        public double getTotalScore() { return totalScore; }
        public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
        
        public ProductMetadata getMetadata() { return metadata; }
        public void setMetadata(ProductMetadata metadata) { this.metadata = metadata; }

        @Override
        public String toString() {
            return "HybridSearchResult{" +
                    "chunkId='" + chunkId + '\'' +
                    ", vectorScore=" + String.format("%.3f", vectorScore) +
                    ", metadataScore=" + String.format("%.3f", metadataScore) +
                    ", totalScore=" + String.format("%.3f", totalScore) +
                    ", hasMetadata=" + (metadata != null) +
                    '}';
        }
    }
}
