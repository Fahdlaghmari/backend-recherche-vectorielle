package com.example.chatbotrag.controller;

import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.model.Document;
import com.example.chatbotrag.repository.ChunkRepository;
import com.example.chatbotrag.repository.DocumentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chunks")
public class ChunkController {

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    public ChunkController(ChunkRepository chunkRepository, DocumentRepository documentRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        System.out.println("[API] ChunkController instancié");
    }

    // ✅ Tous les chunks (option : pagination + filtre par documentId)
    @GetMapping
    public Map<String, String> getChunks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long documentId
    ) {
        List<Chunk> chunks;

        if (documentId != null) {
            Optional<Document> docOpt = documentRepository.findById(documentId);
            chunks = docOpt.map(Document::getChunks).orElse(List.of());
        } else {
            chunks = chunkRepository.findAll();
        }

        int start = Math.min(page * size, chunks.size());
        int end = Math.min(start + size, chunks.size());

        return chunks.subList(start, end).stream()
                .collect(Collectors.toMap(Chunk::getId, Chunk::getText));
    }

    // 🔍 Chunk par ID
    @GetMapping("/{chunkId}")
    public String getChunkById(@PathVariable String chunkId) {
        return chunkRepository.findById(chunkId)
                .map(Chunk::getText)
                .orElse("❌ Chunk introuvable");
    }

    // 🔍 Tous les chunks d’un document (sans pagination)
    @GetMapping("/by-document/{documentId}")
    public Map<String, String> getChunksByDocumentId(@PathVariable Long documentId) {
        return documentRepository.findById(documentId)
                .map(doc -> doc.getChunks().stream()
                        .collect(Collectors.toMap(Chunk::getId, Chunk::getText)))
                .orElse(Map.of());
    }


    // ✏️ Modifier le nom d’un document
    @PutMapping("/documents/{documentId}")
    public String updateDocumentName(
            @PathVariable Long documentId,
            @RequestParam String newName
    ) {
        return documentRepository.findById(documentId)
                .map(doc -> {
                    doc.setName(newName);
                    documentRepository.save(doc);
                    return "✏️ Nouveau nom enregistré pour " + documentId + " → " + newName;
                })
                .orElse("❌ Document introuvable : " + documentId);
    }

    // 🟢 Route de test pour vérifier la connectivité
    @GetMapping("/test")
    public String testApi() {
        System.out.println("[API] /chunks/test appelé");
        return "API chunks OK";
    }

    // 🟢 Liste tous les codes SH présents dans les chunks (tolérance maximale)
    @GetMapping("/sh-codes")
    public org.springframework.http.ResponseEntity<List<String>> getAllShCodes() {
        List<Chunk> chunks = chunkRepository.findAll();
        Set<String> allShCodes = new java.util.HashSet<>();
        java.util.regex.Pattern shPattern = java.util.regex.Pattern.compile("SH[^0-9]*([0-9\\s\\-\\n]{6,20})", java.util.regex.Pattern.CASE_INSENSITIVE);
        for (Chunk chunk : chunks) {
            java.util.regex.Matcher matcher = shPattern.matcher(chunk.getText());
            while (matcher.find()) {
                String codeBrut = matcher.group(1);
                String codeNum = codeBrut.replaceAll("[^0-9]", "");
                if (!codeNum.isEmpty()) {
                    allShCodes.add(codeNum);
                }
            }
        }
        System.out.println("[API] /chunks/sh-codes appelé, codes trouvés: " + allShCodes.size());
        return org.springframework.http.ResponseEntity.ok(new java.util.ArrayList<>(allShCodes));
    }
}
