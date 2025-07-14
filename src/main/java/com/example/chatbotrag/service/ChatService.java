package com.example.chatbotrag.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

@Service
public class ChatService {
    
    private final VectorSearchService vectorSearchService;
    private final HybridSearchService hybridSearchService;
    private final OllamaClientService ollamaClient;
    
    public ChatService(VectorSearchService vectorSearchService, 
                       HybridSearchService hybridSearchService,
                       OllamaClientService ollamaClient) {
        this.vectorSearchService = vectorSearchService;
        this.hybridSearchService = hybridSearchService;
        this.ollamaClient = ollamaClient;
    }

    public List<String> ask(String sessionId, String userQuestion, String forcedLang) {
        System.out.println("[CHAT_SERVICE] 🚀 Début du traitement de la question: " + userQuestion);
        
        // 1. 🎯 Vérifications préliminaires
        if (userQuestion != null) {
            String q = userQuestion.trim().toLowerCase();
            // Reconnaissance des salutations et expressions courantes
            if (q.matches(".*\\b(bonjour|coucou|hello|salut|bonsoir|hi|hey)\\b.*") ||
                q.matches(".*\\b(ca va|cava|ça va|comment ça va|comment ca va)\\b.*") ||
                q.matches(".*\\b(quoi de neuf|comment tu vas|comment allez-vous)\\b.*")) {
                return Collections.singletonList("👋 Bonjour ! Ça va bien, merci ! Comment puis-je vous aider avec vos questions douanières aujourd'hui ?");
            }
            // Reconnaissance des questions générales sur l'aide
            if (q.matches(".*\\b(aide|help|aidez-moi|comment utiliser|que peux-tu faire)\\b.*")) {
                return Collections.singletonList("🤝 Je suis votre assistant douanier spécialisé ! Je peux vous aider avec :\n\n" +
                    "• 📋 Codes SH et classifications tarifaires\n" +
                    "• 💰 Droits de douane et taxes à l'importation\n" +
                    "• 🌍 Accords commerciaux et tarifs préférentiels\n" +
                    "• 📦 Réglementations d'importation au Maroc\n\n" +
                    "Posez-moi une question spécifique sur un produit à importer !");
            }
        }
        if (userQuestion == null || userQuestion.isBlank()) {
            return Collections.singletonList("Désolé, je n'ai pas de réponse pour votre question.");
        }
        
        // 2. 🔍 Recherche hybride des documents pertinents
        System.out.println("[CHAT_SERVICE] 🔍 Recherche hybride des documents pertinents...");
        final int MAX_CONTEXT_CHUNKS = 3; // Réduit pour éviter les timeouts
        
        // Utilisation de la recherche hybride
        List<HybridSearchService.HybridSearchResult> hybridResults = hybridSearchService.searchHybrid(userQuestion, MAX_CONTEXT_CHUNKS);
        
        // Extraction des chunks textuels pour compatibilité
        List<String> topChunks = new ArrayList<>();
        for (HybridSearchService.HybridSearchResult result : hybridResults) {
            topChunks.add(result.getText());
        }
        
        // Debug: Log the chunks found
        System.out.println("[CHAT_SERVICE] 📊 Résultats hybrides trouvés: " + hybridResults.size());
        for (int i = 0; i < hybridResults.size(); i++) {
            HybridSearchService.HybridSearchResult result = hybridResults.get(i);
            String preview = result.getText().substring(0, Math.min(100, result.getText().length())).replace("\n", " ");
            System.out.println("[CHAT_SERVICE] Résultat " + i + " (score: " + 
                               String.format("%.3f", result.getTotalScore()) + "): " + preview + "...");
            
            // Log metadata info if available
            if (result.getMetadata() != null) {
                System.out.println("[CHAT_SERVICE] - Métadonnées: SH=" + result.getMetadata().getCodeSh() + 
                                   ", Type=" + result.getMetadata().getTypeProduit() + 
                                   ", État=" + result.getMetadata().getEtatProduit());
            }
            
            // Check for specific content
            if (result.getText().contains("0101292000")) {
                System.out.println("[CHAT_SERVICE] ✅ TROUVÉ CODE SH 0101292000 dans résultat " + i);
            }
        }
        
        if (topChunks.isEmpty()) {
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
            System.out.println("[DEBUG] PROMPT COMPLET:");
            System.out.println("=" + "=".repeat(80));
            System.out.println(fullPrompt);
            System.out.println("=" + "=".repeat(80));
            
            // 3. 🚀 Appel à Mistral
            String mistralResponse = ollamaClient.generateWithMistral(fullPrompt);
            
            if (mistralResponse != null && !mistralResponse.trim().isEmpty()) {
                System.out.println("[MISTRAL] ✅ Réponse reçue de Mistral (" + mistralResponse.length() + " caractères)");
                System.out.println("[DEBUG] RÉPONSE MISTRAL:");
                System.out.println("-" + "-".repeat(80));
                System.out.println(mistralResponse);
                System.out.println("-" + "-".repeat(80));
                
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
     * 📝 Construction du prompt système pour Mistral (générique pour tous codes SH)
     */
    private String construireMistralPrompt() {
        return "Tu es un assistant douanier expert spécialisé dans les codes SH, droits de douane et réglementations d'importation au Maroc.\n" +
               "\nTon objectif est d'extraire les informations EXACTES des documents fournis et de les présenter de manière structurée.\n" +
               "\nCommence toujours ta réponse par une phrase d'introduction personnalisée selon la question, par exemple :\n" +
               "'Pour l'importation de [produit] au Maroc, voici les informations clés à prendre en compte :'\n" +
               "\nPuis structure ta réponse EXACTEMENT selon ce format professionnel :\n\n" +
               
               "Position Tarifaire\n" +
               "Code SH : [code à 10 chiffres exact trouvé dans le document]\n" +
               "Description : [description complète et précise du produit selon le document, copie exacte].\n\n" +
               
               "Droits et Taxes\n" +
               "Droit d'Importation (DI) : [pourcentage exact trouvé dans le document]\n" +
               "Taxe Parafiscale à l'Importation (TPI) : [pourcentage exact trouvé dans le document]\n" +
               "Taxe sur la Valeur Ajoutée à l'Importation (TVA) : [pourcentage exact trouvé dans le document]\n\n" +
               
               "Accords et Conventions\n" +
               "Les [produits concernés] peuvent bénéficier de droits préférentiels dans le cadre des accords suivants :\n\n" +
               "[Liste UNIQUEMENT les accords trouvés dans les documents avec leurs taux EXACTS]\n\n" +
               
               "RÈGLES STRICTES D'EXTRACTION :\n" +
               "1. COPIE EXACTEMENT les informations comme écrites dans les documents\n" +
               "2. N'invente JAMAIS de valeurs - utilise UNIQUEMENT les données présentes dans les documents\n" +
               "3. Si une information n'est pas présente dans les documents, écris 'Non spécifié dans les documents'\n" +
               "4. CHERCHE tous les codes SH (patterns à 10 chiffres) présents dans les documents\n" +
               "5. IDENTIFIE automatiquement le code SH correspondant au produit de la question\n" +
               "6. Pour les pourcentages, copie exactement les valeurs avec le symbole %\n" +
               "7. Pour les accords commerciaux, liste SEULEMENT ceux mentionnés explicitement avec leurs taux\n" +
               "8. Respecte exactement le format sans emojis ni formatage HTML\n" +
               "9. Remplace [produit] et [produits concernés] par le nom exact du produit de la question\n" +
               "10. Si plusieurs documents sont fournis, utilise celui qui correspond le mieux à la question\n" +
               "11. Privilégie le document contenant le code SH exact correspondant au produit demandé\n" +
               "12. ANALYSE le contenu pour identifier automatiquement le bon code SH même si non mentionné explicitement";
    }
    
    /**
     * 🎨 Formatage de la réponse pour l'affichage web
     */
    private String formatForWeb(String mistralResponse) {
        // Conversion des sauts de ligne en balises HTML
        String formatted = mistralResponse.replace("\n", "<br>");
        
        // Formatage spécial pour les titres (sans emojis)
        formatted = formatted.replace("Position Tarifaire", "<br><strong style='color:#2563eb; font-size:1.2em;'>Position Tarifaire</strong>")
                            .replace("Droits et Taxes", "<br><br><strong style='color:#059669; font-size:1.2em;'>Droits et Taxes</strong>")
                            .replace("Accords et Conventions", "<br><br><strong style='color:#7c3aed; font-size:1.2em;'>Accords et Conventions</strong>");
        
        // Formatage pour les codes SH et descriptions importantes
        formatted = formatted.replace("Code SH :", "<br><strong style='color:#1f2937;'>Code SH :</strong>")
                            .replace("Description :", "<br><strong style='color:#1f2937;'>Description :</strong>");
        
        // Formatage pour les taxes et droits
        formatted = formatted.replace("Droit d'Importation (DI) :", "<br><strong>Droit d'Importation (DI) :</strong>")
                            .replace("Taxe Parafiscale à l'Importation (TPI) :", "<br><strong>Taxe Parafiscale à l'Importation (TPI) :</strong>")
                            .replace("Taxe sur la Valeur Ajoutée à l'Importation (TVA) :", "<br><strong>Taxe sur la Valeur Ajoutée à l'Importation (TVA) :</strong>");
        
        // Formatage pour les accords commerciaux
        formatted = formatted.replace("Union Européenne :", "<br>Union Européenne :")
                            .replace("Ligue Arabe :", "<br>Ligue Arabe :")
                            .replace("Accord d'Agadir :", "<br>Accord d'Agadir :")
                            .replace("États-Unis :", "<br>États-Unis :")
                            .replace("Zone de libre-échange continentale africaine", "<br>Zone de libre-échange continentale africaine")
                            .replace("Émirats Arabes Unis :", "<br>Émirats Arabes Unis :")
                            .replace("Irak :", "<br>Irak :")
                            .replace("Libye :", "<br>Libye :")
                            .replace("Royaume-Uni :", "<br>Royaume-Uni :")
                            .replace("Algérie :", "<br>Algérie :");
        
        // Nettoyage des balises en trop au début
        if (formatted.startsWith("<br>")) {
            formatted = formatted.substring(4);
        }
        
        // Suppression des doubles <br> consécutifs excessifs
        formatted = formatted.replaceAll("(<br>){3,}", "<br><br>");
        
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
