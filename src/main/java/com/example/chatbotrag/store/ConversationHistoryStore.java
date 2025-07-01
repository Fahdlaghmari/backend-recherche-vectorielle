package com.example.chatbotrag.store;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConversationHistoryStore {

    // Stocke l'historique par session
    private final Map<String, List<String>> historyMap = new HashMap<>();

    // 🔄 Récupère l’historique complet (en texte)
    public String getHistory(String sessionId) {
        List<String> history = historyMap.getOrDefault(sessionId, new ArrayList<>());
        return String.join("\n", history);
    }

    // 📝 Ajoute un tour de conversation
    public void append(String sessionId, String question, String answer) {
        historyMap.putIfAbsent(sessionId, new ArrayList<>());
        historyMap.get(sessionId).add("👤 " + question);
        historyMap.get(sessionId).add("🤖 " + answer);
    }

    // 🧹 Supprime l'historique d’une session
    public void clear(String sessionId) {
        historyMap.remove(sessionId);
    }

    // 🧠 Récupère les N derniers messages (ex: 10 pour 5 tours)
    public List<String> getLastMessages(String sessionId, int count) {
        List<String> history = historyMap.getOrDefault(sessionId, new ArrayList<>());
        int start = Math.max(0, history.size() - count);
        return history.subList(start, history.size());
    }
}
