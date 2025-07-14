package com.example.chatbotrag.controller;

import com.example.chatbotrag.service.ChatService;
import com.example.chatbotrag.store.ConversationHistoryStore;
import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.repository.ChunkRepository;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.UUID; // Pour générer un sessionId si besoin
import java.util.List;

@RestController
@RequestMapping("/chat") // J'ai gardé /chat, adaptez si c'est /api/chat
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final ChatService chatService;
    private final ConversationHistoryStore conversationHistoryStore;
    private final ChunkRepository chunkRepository;
    

    @org.springframework.beans.factory.annotation.Autowired
    public ChatController(ChatService chatService, ConversationHistoryStore conversationHistoryStore, ChunkRepository chunkRepository) {
        this.chatService = chatService;
        this.conversationHistoryStore = conversationHistoryStore;
        this.chunkRepository = chunkRepository;
    }

    // DTO pour la requête de chat, si vous préférez un corps de requête POST (plus propre pour le streaming)
    // Si vous gardez GET, vous n'avez pas besoin de ce DTO pour la méthode 'askStream'.
    public static class ChatRequest {
        private String sessionId;
        private String question;
        private String lang;

        // Getters et Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getLang() { return lang; }
        public void setLang(String lang) { this.lang = lang; }
    }

    // Endpoint GET simple pour tester l'API
    @GetMapping
    public org.springframework.http.ResponseEntity<List<String>> ask(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam(required = false) String lang) {
        try {
            List<String> responses = chatService.ask(sessionId, question, lang);
            return org.springframework.http.ResponseEntity.ok(responses);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError()
                .body(java.util.Collections.singletonList("Erreur lors du traitement: " + e.getMessage()));
        }
    }

    // Endpoint pour le chat en streaming. Préférable avec GET pour EventSource natif,
    // ou POST si votre client sait gérer le stream après un POST.
    // Spring WebFlux gère bien le retour de Flux<String> avec produces = TEXT_EVENT_STREAM_VALUE sur POST ou GET.
    @PostMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter askStream(@RequestBody ChatRequest chatRequest) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        new Thread(() -> {
            try {
                String sessionId = chatRequest.getSessionId();
                if (sessionId == null || sessionId.isBlank()) {
                    sessionId = java.util.UUID.randomUUID().toString();
                    System.out.println("DEBUG ChatController: No sessionId provided, generated new one: " + sessionId + ". Client will not know this ID for future requests unless handled.");
                }
                java.util.List<String> responses = chatService.ask(
                    sessionId,
                    chatRequest.getQuestion(),
                    chatRequest.getLang()
                );
                for (String chunk : responses) {
                    emitter.send(chunk + "\n"); // Ajoute un saut de ligne pour chaque chunk
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    // Endpoint pour récupérer l'historique d'une session
    @GetMapping("/history")
    public ResponseEntity<String> getHistory(@RequestParam String sessionId) {
        try {
            String history = conversationHistoryStore.getHistory(sessionId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Erreur lors de la récupération de l'historique: " + e.getMessage());
        }
    }

    // Endpoint pour supprimer l'historique d'une session
    @DeleteMapping("/history")
    public ResponseEntity<String> deleteHistory(@RequestParam String sessionId) {
        try {
            conversationHistoryStore.clear(sessionId);
            return ResponseEntity.ok("Historique supprimé pour la session: " + sessionId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Erreur lors de la suppression de l'historique: " + e.getMessage());
        }
    }
    
    /**
     * 🔍 Endpoint de debug pour rechercher des chunks spécifiques
     */
    @GetMapping("/debug/search/{code}")
    public ResponseEntity<?> searchChunk(@PathVariable String code) {
        try {
            // Recherche dans la base MySQL
            Optional<Chunk> chunk = chunkRepository.findFirstByKeyword(code);
            
            Map<String, Object> result = new HashMap<>();
            result.put("searchCode", code);
            result.put("found", chunk.isPresent());
            
            if (chunk.isPresent()) {
                result.put("chunkId", chunk.get().getId());
                result.put("chunkText", chunk.get().getText());
                result.put("textLength", chunk.get().getText().length());
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("❌ Erreur lors de la recherche de debug", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 🔍 Endpoint pour rechercher des chunks par mots-clés
     */
    @GetMapping("/debug/search")
    public ResponseEntity<?> searchByKeyword(@RequestParam String keyword) {
        try {
            List<Chunk> chunks = chunkRepository.findByKeywordAndCodeSh(keyword, "0106201000");
            
            Map<String, Object> result = new HashMap<>();
            result.put("keyword", keyword);
            result.put("code", "0106201000");
            result.put("chunksFound", chunks.size());
            
            List<Map<String, Object>> chunkDetails = new ArrayList<>();
            for (Chunk chunk : chunks) {
                Map<String, Object> details = new HashMap<>();
                details.put("id", chunk.getId());
                details.put("textLength", chunk.getText().length());
                details.put("preview", chunk.getText().substring(0, Math.min(200, chunk.getText().length())));
                chunkDetails.add(details);
            }
            result.put("chunks", chunkDetails);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("❌ Erreur lors de la recherche par mot-clé", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 🔧 Endpoint pour corriger le chunk existant tronqué
     */
    @PostMapping("/debug/fix-mammal-chunk")
    public ResponseEntity<?> fixMammalChunk() {
        try {
            // 1. Trouver le chunk existant
            Optional<Chunk> existingChunk = chunkRepository.findFirstByKeyword("0106201000");
            
            if (!existingChunk.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Chunk avec code 0106201000 non trouvé"
                ));
            }
            
            Chunk chunk = existingChunk.get();
            
            // 2. Données complètes pour remplacer le contenu tronqué
            String completeData = "le code SH 0106201000 correspond à la catégorie Animaux vivants, à la sous catégorie Autres animaux vivants£- Mammifères :. Ce code identifie - - - destinés aux parcs zoologiques, nationaux ou des collectivités locales, importés à leur ordre ou pour leur compte et conduits directement à ces parcs£- - - autres :\n\n" +
                    "L'importation au Maroc des produits relatifs à cette position est soumise à des mesures tarifaires et non tarifaires imposées par la réglementation en vigueur.\n\n" +
                    "Les mesures tarifaires applicables à cette position sont les droits de douanes suivants :\n" +
                    "- Droit d'Importation* ( DI ) : 2,5 %\n" +
                    "- Taxe Parafiscale à l'Importation* ( TPI ) : 0,25 %\n" +
                    "- Taxe sur la Valeur Ajoutée à l'Import. ( TVA ) : 20 %\n\n" +
                    "les tarifs préferentiels prévus par les accords et conventions sont comme suit :\n" +
                    "importation Union Européenne un droit préferentiel de 0%.\n" +
                    "importation L. ARABE un droit préferentiel de 0%.\n" +
                    "importation Accord d'Agadir un droit préferentiel de 0%.\n" +
                    "importation Etat Unis un droit préferentiel de 0%.\n" +
                    "importation Zone de libre échange continentale africaine ( 5ans) un droit préferentiel de 0,5%.\n" +
                    "importation Zone de libre échange continentale africaine ( 10ans) un droit préferentiel de 1,5%.\n" +
                    "importation EMIRAT un droit préferentiel de 0%.\n" +
                    "importation IRAK un droit préferentiel de 0.0%.\n" +
                    "importation LIBYA un droit préferentiel de 0.0%.\n" +
                    "importation Royaume Uni un droit préferentiel de 0%.\n" +
                    "importation ALGERIE un droit préferentiel de 0.0%.";
            
            // 3. Mettre à jour le chunk existant
            String oldText = chunk.getText();
            chunk.setText(completeData);
            chunkRepository.save(chunk);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Chunk existant corrigé avec données complètes");
            result.put("chunkId", chunk.getId());
            result.put("oldLength", oldText.length());
            result.put("newLength", completeData.length());
            result.put("improvement", completeData.length() - oldText.length() + " caractères ajoutés");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("❌ Erreur lors de la correction du chunk", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Fix error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 📝 Endpoint pour injecter manuellement les données correctes pour les mammifères
     */
    @PostMapping("/debug/inject-mammal-data")
    public ResponseEntity<?> injectMammalData() {
        try {
            // Données complètes pour mammifères de parcs zoologiques
            String completeData = "le code SH 0106201000 correspond à la catégorie Animaux vivants, à la sous catégorie Autres animaux vivants£- Mammifères :. Ce code identifie - - - destinés aux parcs zoologiques, nationaux ou des collectivités locales, importés à leur ordre ou pour leur compte et conduits directement à ces parcs£- - - autres : \n\n" +
                    "L'importation au Maroc des produits relatifs à cette position est soumise à des mesures tarifaires et non tarifaires imposées par la réglementation en vigueur.\n\n" +
                    "Les mesures tarifaires applicables à cette position sont les droits de douanes suivants :\n" +
                    "- Droit d'Importation* ( DI ) : 2,5 %\n" +
                    "- Taxe Parafiscale à l'Importation* ( TPI ) : 0,25 %\n" +
                    "- Taxe sur la Valeur Ajoutée à l'Import. ( TVA ) : 20 %\n\n" +
                    "les tarifs préferentiels prévus par les accords et conventions sont comme suit :\n" +
                    "importation Union Européenne un droit préferentiel de 0%.\n" +
                    "importation L. ARABE un droit préferentiel de 0%.\n" +
                    "importation Accord d'Agadir un droit préferentiel de 0%.\n" +
                    "importation Etat Unis un droit préferentiel de 0%.\n" +
                    "importation Zone de libre échange continentale africaine ( 5ans) un droit préferentiel de 0,5%.\n" +
                    "importation Zone de libre échange continentale africaine ( 10ans) un droit préferentiel de 1,5%.\n" +
                    "importation EMIRAT un droit préferentiel de 0%.\n" +
                    "importation IRAK un droit préferentiel de 0.0%.\n" +
                    "importation LIBYA un droit préferentiel de 0.0%.\n" +
                    "importation Royaume Uni un droit préferentiel de 0%.\n" +
                    "importation ALGERIE un droit préferentiel de 0.0%.";
            
            // Création du chunk avec ID unique
            Chunk newChunk = new Chunk();
            newChunk.setId("MAMMAL_ZOO_COMPLETE_0106201000");
            newChunk.setText(completeData);
            // Note: Document peut être null pour injection manuelle
            
            // Sauvegarde dans MySQL
            chunkRepository.save(newChunk);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Données complètes injectées pour mammifères de parcs zoologiques");
            result.put("chunkId", newChunk.getId());
            result.put("dataLength", completeData.length());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'injection des données", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Injection error: " + e.getMessage()
            ));
        }
    }

}
