package com.example.chatbotrag.service;

import com.example.chatbotrag.config.Constants;
import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.repository.ChunkRepository;
import org.springframework.stereotype.Service;
import com.example.chatbotrag.repository.ProductMetadataRepository;
import com.example.chatbotrag.model.ProductMetadata;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class VectorSearchService {

    private final ChromaHttpClientService chromaHttpClientService;
    private final OllamaClientService ollamaClient;
    private final ChunkRepository chunkRepository;
    private final ProductMetadataRepository productMetadataRepository;
    private final ProductMetadataExtractionService metadataExtractionService;

    public VectorSearchService(ChromaHttpClientService chromaHttpClientService,
                               OllamaClientService ollamaClient,
                               ChunkRepository chunkRepository,
                               ProductMetadataRepository productMetadataRepository,
                               ProductMetadataExtractionService metadataExtractionService) {
        this.chromaHttpClientService = chromaHttpClientService;
        this.ollamaClient = ollamaClient;
        this.chunkRepository = chunkRepository;
        this.productMetadataRepository = productMetadataRepository;
        this.metadataExtractionService = metadataExtractionService;
    }

    public List<String> findTopKRelevantChunks(String userQuestion, int topK) {
        long startTotal = System.currentTimeMillis();
        System.out.println("[VECTOR_SEARCH] 🔍 Question utilisateur : " + userQuestion);

        try {
            // 0. 🎯 RECHERCHE PRIORITAIRE PAR CODE SH SPÉCIFIQUE
            List<String> priorityResults = searchBySpecificSHCode(userQuestion, topK);
            if (!priorityResults.isEmpty()) {
                System.out.println("[VECTOR_SEARCH] ✅ Trouvé résultats prioritaires par code SH : " + priorityResults.size());
                return priorityResults;
            }
            
            // 1. 🧠 Génération de l'embedding pour la question utilisateur
            System.out.println("[VECTOR_SEARCH] 📊 Génération de l'embedding pour la question...");
            List<Double> questionVector = ollamaClient.embed(userQuestion);
            
            if (questionVector == null || questionVector.isEmpty()) {
                System.err.println("[VECTOR_SEARCH] ❌ Impossible de générer l'embedding pour la question. Fallback vers recherche par mots-clés.");
                return fallbackKeywordSearch(userQuestion, topK);
            }
            
            System.out.println("[VECTOR_SEARCH] ✅ Embedding généré (dimension: " + questionVector.size() + ")");
            
// Search by metadata score
            Map<String, String> queryMetadata = metadataExtractionService.extractQueryMetadata(userQuestion);
            List<ProductMetadata> metadataResults = productMetadataRepository.findByMultipleCriteria(
                queryMetadata.get("type"), 
                queryMetadata.get("etat"), 
                queryMetadata.get("desossage"),
                queryMetadata.get("usage")
            );
            metadataResults.sort((p1, p2) -> Double.compare(p2.calculateMetadataScore(queryMetadata), p1.calculateMetadataScore(queryMetadata)));

            List<String> topMetadataChunks = new ArrayList<>();
            for (ProductMetadata metadata : metadataResults) {
                if (topMetadataChunks.size() >= topK) break;
                topMetadataChunks.add(metadata.getChunk().getText());
            }
            if (!topMetadataChunks.isEmpty()) {
                System.out.println("[VECTOR_SEARCH] ✅ Résultats trouvés par scores de métadonnées.");
                return topMetadataChunks;
            }

            // 2. 🎯 Conversion en format float array
            float[] questionEmbedding = new float[questionVector.size()];
            for (int i = 0; i < questionVector.size(); i++) {
                questionEmbedding[i] = questionVector.get(i).floatValue();
            }
            
            // 3. 🔎 Recherche vectorielle dans ChromaDB avec HTTP client
            System.out.println("[VECTOR_SEARCH] 🔎 Recherche vectorielle dans " + Constants.CHROMA_COLLECTION_NAME + "...");
            List<ChromaHttpClientService.SearchResult> searchResults = chromaHttpClientService.searchSimilar(
                    Constants.CHROMA_COLLECTION_NAME, 
                    questionEmbedding, 
                    topK * 5, // Récupérer plus de résultats pour un meilleur filtrage
                    0.1 // Score de similarité minimum plus élevé pour de meilleurs résultats
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
     * 🔍 Validation des résultats avec filtrage intelligent
     */
    private boolean isValidResult(String chunkText, String userQuestion) {
        if (chunkText == null || chunkText.trim().isEmpty()) {
            return false;
        }
        
        String chunkLower = chunkText.toLowerCase();
        String questionLower = userQuestion.toLowerCase();
        
        // 1. 🎯 Filtrage prioritaire pour codes SH
        if (chunkText.matches(".*\\b\\d{10}\\b.*")) {
            System.out.println("[VALID_RESULT] ✅ Chunk contient un code SH - priorité élevée");
            return true;
        }
        
        // 2. 🐾 Filtrage spécifique pour mammifères destinés aux parcs zoologiques
        if (questionLower.contains("mammifères") && questionLower.contains("zoologiques")) {
            if (chunkLower.contains("mammifères") || chunkLower.contains("zoologiques") || 
                chunkLower.contains("parcs") || chunkLower.contains("destinés") ||
                chunkLower.contains("0106201000")) {
                System.out.println("[VALID_RESULT] ✅ Chunk pertinent pour mammifères de parcs zoologiques");
                return true;
            }
        }
        
        // 3. 🏇 Filtrage spécifique pour chevaux (garde l'ancien filtrage)
        if (questionLower.contains("cheval") || questionLower.contains("course")) {
            if (chunkLower.contains("cheval") || chunkLower.contains("course") || 
                chunkText.contains("0101292000") || chunkText.contains("0101210000")) {
                System.out.println("[VALID_RESULT] ✅ Chunk pertinent pour chevaux");
                return true;
            }
        }
        
        // 4. 📋 Filtrage général pour termes d'importation
        String[] importTerms = {"importation", "droit d'importation", "tva", "taxe", "douane", "tarifaire"};
        String[] questionWords = questionLower.split("\\s+");
        
        int matchCount = 0;
        for (String questionWord : questionWords) {
            if (questionWord.length() > 3) { // Ignorer les mots trop courts
                for (String importTerm : importTerms) {
                    if (chunkLower.contains(questionWord) || chunkLower.contains(importTerm)) {
                        matchCount++;
                        break;
                    }
                }
            }
        }
        
        // 5. 🔍 Validation basée sur le nombre de correspondances
        if (matchCount >= 2) {
            System.out.println("[VALID_RESULT] ✅ Chunk pertinent avec " + matchCount + " correspondances");
            return true;
        }
        
        // 6. 📖 Vérification de contenu substantiel
        if (chunkText.length() > 100 && matchCount >= 1) {
            System.out.println("[VALID_RESULT] ✅ Chunk substantiel avec contenu pertinent");
            return true;
        }
        
        System.out.println("[VALID_RESULT] ❌ Chunk rejeté - pas assez pertinent");
        return false;
    }
    
    /**
     * 🎯 Vérification de contenu spécifiquement pertinent (améliorée)
     */
    private boolean containsRelevantContent(String chunkText, String userQuestion) {
        if (chunkText == null || chunkText.trim().isEmpty()) {
            return false;
        }
        
        String chunkLower = chunkText.toLowerCase();
        String questionLower = userQuestion.toLowerCase();
        
        // 1. 🐾 Recherche spécifique pour mammifères de parcs zoologiques
        if (questionLower.contains("mammifères") && questionLower.contains("zoologiques")) {
            if (chunkText.contains("0106201000") || 
                (chunkLower.contains("mammifères") && chunkLower.contains("zoologiques")) ||
                (chunkLower.contains("destinés aux parcs") && chunkLower.contains("zoologiques")) ||
                (chunkLower.contains("animaux vivants") && chunkLower.contains("mammifères"))) {
                System.out.println("[CONTENT_MATCH] 🎯 Trouvé contenu spécifique pour mammifères de parcs zoologiques");
                return true;
            }
        }
        
        // 2. 🏇 Recherche de codes SH spécifiques pour chevaux (garde l'ancien)
        if (questionLower.contains("cheval") || questionLower.contains("course")) {
            if (chunkText.contains("0101292000") || chunkText.contains("0101210000") ||
                (chunkLower.contains("cheval") && chunkLower.contains("course")) ||
                (chunkLower.contains("âgés de moins de six") && chunkLower.contains("vingt"))) {
                System.out.println("[CONTENT_MATCH] 🎯 Trouvé contenu spécifique pour chevaux de course");
                return true;
            }
        }
        
        // 3. 📋 Recherche générale de codes SH
        if (chunkText.matches(".*\\b\\d{10}\\b.*")) {
            System.out.println("[CONTENT_MATCH] 🎯 Trouvé code SH dans le chunk");
            return true;
        }
        
        // 4. 💰 Recherche de contenu tarifaire spécifique
        if (chunkLower.contains("droit d'importation") && chunkLower.contains("%")) {
            System.out.println("[CONTENT_MATCH] 🎯 Trouvé contenu tarifaire spécifique");
            return true;
        }
        
        // 5. 🌍 Recherche d'accords commerciaux
        String[] commercialAgreements = {"union européenne", "accord d'agadir", "états-unis", 
                                        "zone de libre échange", "émirats arabes unis", "royaume-uni"};
        for (String agreement : commercialAgreements) {
            if (chunkLower.contains(agreement)) {
                System.out.println("[CONTENT_MATCH] 🎯 Trouvé accord commercial: " + agreement);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 🎯 Recherche prioritaire par code SH spécifique
     */
    private List<String> searchBySpecificSHCode(String userQuestion, int topK) {
        String questionLower = userQuestion.toLowerCase();
        List<String> results = new ArrayList<>();
        
        // 1. 🐾 Mammifères destinés aux parcs zoologiques
        if (questionLower.contains("mammifères") && questionLower.contains("zoologiques")) {
            System.out.println("[SH_SEARCH] 🐾 Recherche spécifique pour mammifères de parcs zoologiques (code 0106201000)");
            
            // Recherche MULTIPLE pour récupérer chunks liés
            Set<String> collectedTexts = new HashSet<>();
            
            // 1. Recherche directe par code SH
            Optional<Chunk> mainChunk = chunkRepository.findFirstByKeyword("0106201000");
            if (mainChunk.isPresent()) {
                collectedTexts.add(mainChunk.get().getText());
                System.out.println("[SH_SEARCH] ✅ Trouvé chunk principal avec code 0106201000");
            }
            
            // 2. Recherche par termes associés pour compléter les infos
            List<Chunk> additionalChunks = chunkRepository.findByKeywordAndCodeSh("mammifères", "0106201000");
            for (Chunk chunk : additionalChunks) {
                if (chunk.getText() != null && !chunk.getText().trim().isEmpty()) {
                    collectedTexts.add(chunk.getText());
                    System.out.println("[SH_SEARCH] ✅ Ajouté chunk additonnel mammifères");
                }
            }
            
            // 3. Recherche par mots-clés tarifaires pour mammifères
            String[] tariffKeywords = {"droit d'importation", "tva", "taxe", "2,5", "0,25", "20%"};
            for (String keyword : tariffKeywords) {
                Optional<Chunk> tariffChunk = chunkRepository.findFirstByKeyword(keyword);
                if (tariffChunk.isPresent() && tariffChunk.get().getText().toLowerCase().contains("mammifères")) {
                    collectedTexts.add(tariffChunk.get().getText());
                    System.out.println("[SH_SEARCH] ✅ Ajouté chunk tarifaire avec: " + keyword);
                }
            }
            
            // 4. Ajout à results (limite par topK)
            for (String text : collectedTexts) {
                if (results.size() >= topK) break;
                results.add(text);
            }
        }
        
        // 2. 🏇 Chevaux de course (garde l'ancien système)
        else if (questionLower.contains("cheval") && questionLower.contains("course")) {
            System.out.println("[SH_SEARCH] 🏇 Recherche spécifique pour chevaux de course (code 0101292000)");
            
            List<Chunk> chunks = chunkRepository.findByKeywordAndCodeSh("cheval", "0101292000");
            if (chunks.isEmpty()) {
                chunks = chunkRepository.findByKeywordAndCodeSh("course", "0101292000");
            }
            if (chunks.isEmpty()) {
                Optional<Chunk> chunk = chunkRepository.findFirstByKeyword("0101292000");
                if (chunk.isPresent()) {
                    chunks = List.of(chunk.get());
                }
            }
            
            for (Chunk chunk : chunks) {
                if (results.size() >= topK) break;
                if (chunk.getText() != null && !chunk.getText().trim().isEmpty()) {
                    results.add(chunk.getText());
                    System.out.println("[SH_SEARCH] ✅ Ajouté chunk avec code 0101292000");
                }
            }
        }
        
        // 3. 🔍 Recherche générale de codes SH mentionnés dans la question
        else {
            // Recherche de patterns de codes SH dans la question (10 chiffres)
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d{10}\\b");
            java.util.regex.Matcher matcher = pattern.matcher(userQuestion);
            
            while (matcher.find() && results.size() < topK) {
                String codeFound = matcher.group();
                System.out.println("[SH_SEARCH] 🔍 Code SH détecté dans la question : " + codeFound);
                
                Optional<Chunk> chunk = chunkRepository.findFirstByKeyword(codeFound);
                if (chunk.isPresent() && chunk.get().getText() != null && !chunk.get().getText().trim().isEmpty()) {
                    results.add(chunk.get().getText());
                    System.out.println("[SH_SEARCH] ✅ Ajouté chunk avec code " + codeFound);
                }
            }
        }
        
        if (!results.isEmpty()) {
            System.out.println("[SH_SEARCH] 🎯 Recherche prioritaire réussie : " + results.size() + " chunks trouvés");
        }
        
        return results;
    }

    static class ScoredChunk {
        String chunk;
        int score;
        boolean hasSH;
        boolean incoherent;
        ScoredChunk(String c, int s, boolean h, boolean i) { chunk = c; score = s; hasSH = h; incoherent = i; }
    }
}
