package com.example.chatbotrag.controller;

import com.example.chatbotrag.service.ChatService;
import com.example.chatbotrag.store.ConversationHistoryStore;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.UUID; // Pour générer un sessionId si besoin
import java.util.List;

@RestController
@RequestMapping("/chat") // J'ai gardé /chat, adaptez si c'est /api/chat
public class ChatController {

    private final ChatService chatService;
    private final ConversationHistoryStore conversationHistoryStore;
    

    @org.springframework.beans.factory.annotation.Autowired
    public ChatController(ChatService chatService, ConversationHistoryStore conversationHistoryStore) {
        this.chatService = chatService;
        this.conversationHistoryStore = conversationHistoryStore;
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

}
