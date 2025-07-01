package com.example.chatbotrag.controller;

import com.example.chatbotrag.model.Document;
import com.example.chatbotrag.repository.DocumentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;

    public DocumentController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    // 🟢 Liste tous les documents enregistrés (id, nom, langue, nb chunks)
    @GetMapping
    public List<DocumentDto> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(doc -> new DocumentDto(
                        doc.getId(),
                        doc.getName(),
                        doc.getLanguage(),
                        doc.getChunks() != null ? doc.getChunks().size() : 0
                ))
                .toList();
    }

    // ✅ DTO (Data Transfer Object)
    public record DocumentDto(Long id, String name, String language, int chunkCount) {}
}
