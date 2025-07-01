package com.example.chatbotrag.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

@Service
public class ChatService {
    
    private final VectorSearchService vectorSearchService;
    private final OllamaClientService ollamaClient;
    
    public ChatService(VectorSearchService vectorSearchService, OllamaClientService ollamaClient) {
        this.vectorSearchService = vectorSearchService;
        this.ollamaClient = ollamaClient;
    }

    public List<String> ask(String sessionId, String userQuestion, String forcedLang) {
        System.out.println("[CHAT_SERVICE] 🚀 Début du traitement de la question: " + userQuestion);
        
        // 1. 🎯 Vérifications préliminaires
        if (userQuestion != null) {
            String q = userQuestion.trim().toLowerCase();
            if (q.matches(".*\\b(bonjour|coucou|hello|salut|bonsoir|hi|hey)\\b.*")) {
                return Collections.singletonList("👋 Bonjour ! Comment puis-je vous aider aujourd'hui ?");
            }
        }
        if (userQuestion == null || userQuestion.isBlank()) {
            return Collections.singletonList("Désolé, je n'ai pas de réponse pour votre question.");
        }
        
        // 2. 🔍 Recherche vectorielle des documents pertinents
        System.out.println("[CHAT_SERVICE] 🔍 Recherche vectorielle des documents pertinents...");
        final int MAX_CONTEXT_CHUNKS = 8; // Augmenté pour avoir plus de contexte
        List<String> topChunks = vectorSearchService.findTopKRelevantChunks(userQuestion, MAX_CONTEXT_CHUNKS);
        
        // Debug: Log the chunks found
        System.out.println("[CHAT_SERVICE] 📊 Chunks trouvés: " + topChunks.size());
        for (int i = 0; i < topChunks.size(); i++) {
            String preview = topChunks.get(i).substring(0, Math.min(100, topChunks.get(i).length())).replace("\n", " ");
            System.out.println("[CHAT_SERVICE] Chunk " + i + ": " + preview + "...");
            
            // Check for specific content
            if (topChunks.get(i).contains("0101292000")) {
                System.out.println("[CHAT_SERVICE] ✅ TROUVÉ CODE SH 0101292000 dans chunk " + i);
            }
        }
        
        if (topChunks == null || topChunks.isEmpty()) {
            return Collections.singletonList("Désolé, je n'ai pas d'information sur ce sujet dans ma base documentaire.");
        }
        
        // 3. 🤖 Mode de réponse: Choisir entre REGEX (legacy) ou LLM (Mistral)
        boolean useLLM = shouldUseLLM(userQuestion, topChunks);
        
        if (useLLM) {
            System.out.println("[CHAT_SERVICE] 🤖 Mode LLM activé - Génération avec Mistral");
            return generateLLMResponse(userQuestion, topChunks, forcedLang);
        } else {
            System.out.println("[CHAT_SERVICE] ⚙️ Mode REGEX activé - Traitement traditionnel");
            return generateFallbackResponse(userQuestion, topChunks);
        }
    }
    
    /**
     * 🧠 Décision intelligente : utiliser LLM ou REGEX
     */
    private boolean shouldUseLLM(String userQuestion, List<String> chunks) {
        // Critères pour utiliser le LLM:
        // 1. Question complexe (plus de 8 mots)
        // 2. Question contenant des mots interrogatifs
        // 3. Contexte riche (plus de 2 chunks trouvés)
        
        String[] questionWords = userQuestion.split("\\s+");
        boolean isComplexQuestion = questionWords.length > 6;
        
        String questionLower = userQuestion.toLowerCase();
        boolean hasInterrogativeWords = questionLower.matches(".*(comment|pourquoi|quand|combien|quel|quelle|quels|quelles|expliquer|détails|différence).*");
        
        boolean hasRichContext = chunks.size() > 1;
        
        // Pour l'instant, utilisons toujours le LLM si des chunks sont trouvés
        return !chunks.isEmpty();
    }
    
    /**
     * 🤖 Génération de réponse avec Mistral LLM
     */
    private List<String> generateLLMResponse(String userQuestion, List<String> chunks, String forcedLang) {
        try {
            // 1. 📋 Construction du contexte
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("CONTEXTE DOCUMENTAIRE:\n");
            for (int i = 0; i < chunks.size(); i++) {
                contextBuilder.append("Document ").append(i + 1).append(":\n");
                contextBuilder.append(chunks.get(i)).append("\n\n");
            }
            
            // 2. 📝 Construction du prompt pour Mistral
            String systemPrompt = construireMistralPrompt();
            String fullPrompt = systemPrompt + "\n\n" + contextBuilder.toString() + 
                               "\nQUESTION: " + userQuestion + 
                               "\n\nRÉPONSE (respectez strictement le format demandé):";
            
            System.out.println("[MISTRAL] 📤 Envoi du prompt à Mistral...");
            
            // 3. 🚀 Appel à Mistral
            String mistralResponse = ollamaClient.generateWithMistral(fullPrompt);
            
            if (mistralResponse != null && !mistralResponse.trim().isEmpty()) {
                System.out.println("[MISTRAL] ✅ Réponse reçue de Mistral (" + mistralResponse.length() + " caractères)");
                
                // 4. 🎨 Post-traitement pour l'affichage web
                String formattedResponse = formatForWeb(mistralResponse);
                return Collections.singletonList(formattedResponse);
            } else {
                System.err.println("[MISTRAL] ❌ Réponse vide de Mistral, fallback vers réponse basique");
                return generateFallbackResponse(userQuestion, chunks);
            }
            
        } catch (Exception e) {
            System.err.println("[MISTRAL] ❌ Erreur lors de la génération LLM: " + e.getMessage());
            e.printStackTrace();
            return generateFallbackResponse(userQuestion, chunks);
        }
    }
    
    /**
     * 📝 Construction du prompt système pour Mistral
     */
    private String construireMistralPrompt() {
        return "Tu es un assistant douanier expert spécialisé dans les codes SH, droits de douane et réglementations d'importation au Maroc.\n" +
               "\nTu dois répondre STRICTEMENT au format suivant (utilise EXACTEMENT ces emojis et cette structure):\n" +
               "\n📌 Code SH : [extrait le code à 10 chiffres EXACTEMENT comme dans le document]\n" +
               "📦 Description : [description complète du produit]\n" +
               "📊 Droits & Taxes :\n" +
               "  • DI (Droit d'Importation) : [pourcentage exact du document]\n" +
               "  • TPI (Taxe Parafiscale à l'Importation) : [pourcentage exact du document]\n" +
               "  • TVA (Taxe sur la Valeur Ajoutée) : [pourcentage exact du document]\n" +
               "🧾 Contingents & Quotas : [recopie les informations exactes sur les contingents]\n" +
               "🤝 Accords et Conventions : [liste tous les accords avec leurs taux préférentiels]\n" +
               "\nRÈGLES CRITIQUES :\n" +
               "1. Cherche spécifiquement le code SH à 10 chiffres dans les documents fournis\n" +
               "2. Extrais les informations EXACTEMENT comme écrites, avec les mêmes chiffres et pourcentages\n" +
               "3. Pour les chevaux de course, cherche le code 0101292000 spécifiquement\n" +
               "4. Si tu trouves des informations partielles, utilise-les quand même\n" +
               "5. N'invente JAMAIS de valeurs - utilise uniquement ce qui est dans les documents\n" +
               "6. Si une section n'a pas d'information, écris 'Non précisé dans les documents fournis'\n" +
               "7. Respecte EXACTEMENT le format avec les emojis et puces";
    }
    
    /**
     * 🎨 Formatage de la réponse pour l'affichage web
     */
    private String formatForWeb(String mistralResponse) {
        // Conversion des sauts de ligne en balises HTML
        String formatted = mistralResponse.replace("\n", "<br>")
                                         .replace("📌", "<br>📌")
                                         .replace("📦", "<br>📦")
                                         .replace("📊", "<br>📊")
                                         .replace("🧾", "<br>🧾")
                                         .replace("🤝", "<br>🤝");
        
        // Nettoyage des balises en trop au début
        if (formatted.startsWith("<br>")) {
            formatted = formatted.substring(4);
        }
        
        return formatted;
    }
    
    /**
     * ⚙️ Méthode de fallback simple
     */
    private List<String> generateFallbackResponse(String userQuestion, List<String> chunks) {
        System.out.println("[FALLBACK] ⚙️ Génération de réponse basique");
        
        // Réponse simple basée sur les chunks trouvés
        StringBuilder response = new StringBuilder();
        response.append("📋 Informations trouvées dans la base documentaire :<br><br>");
        
        for (int i = 0; i < Math.min(chunks.size(), 3); i++) {
            String chunk = chunks.get(i);
            // Extraire les premières lignes importantes
            String preview = chunk.length() > 200 ? chunk.substring(0, 200) + "..." : chunk;
            response.append("📄 Extrait ").append(i + 1).append(":<br>");
            response.append(preview.replace("\n", "<br>"));
            response.append("<br><br>");
        }
        
        response.append("ℹ️ Pour une analyse détaillée, veuillez préciser votre question.");
        
        return Collections.singletonList(response.toString());
    }
}
