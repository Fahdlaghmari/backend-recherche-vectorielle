package com.example.chatbotrag.controller;

import com.example.chatbotrag.service.DeleteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/delete")
public class DeleteController {

    private final DeleteService deleteService;

    public DeleteController(DeleteService deleteService) {
        this.deleteService = deleteService;
    }

    // 🔸 Supprimer un chunk spécifique par ID
    @DeleteMapping("/chunk/{chunkId}")
    public String deleteChunk(@PathVariable String chunkId) {
        deleteService.deleteChunkById(chunkId);
        return "✅ Chunk supprimé : " + chunkId;
    }

    // 🔸 Supprimer tous les chunks d’un document
    @DeleteMapping("/document/{documentId}")
    public String deleteDocument(@PathVariable String documentId) {
        deleteService.deleteDocumentById(documentId);
        return "🧼 Document supprimé : " + documentId;
    }
}
