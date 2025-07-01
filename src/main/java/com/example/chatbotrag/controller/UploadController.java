package com.example.chatbotrag.controller;

import com.example.chatbotrag.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// IOException n'est plus explicitement gérée ici si le service s'en charge
// ou si vous décidez de ne plus créer de fichier temporaire dans le contrôleur.
// import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/upload")
public class UploadController {

    private final DocumentIngestionService ingestionService;

    public UploadController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    // Renommage de la méthode pour refléter une gestion plus générique des documents
    public ResponseEntity<String> uploadDocuments(@RequestParam("files") MultipartFile[] files) {
        List<String> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            try {
                // Nous allons appeler une nouvelle méthode (ou modifiée) dans le service
                // qui prendra directement le MultipartFile.
                // Le service se chargera de la logique de création de fichier temporaire si besoin
                // et de la distinction entre PDF, DOCX, etc.
                String docId = ingestionService.ingestDocument(file); // Appel à la nouvelle méthode du service

                responses.add("✅ " + originalFilename + " → Demande d'ingestion reçue (ID: " + docId + ")");
            } catch (Exception e) {
                // Il est bon de prévoir une exception plus générique ici,
                // car le service pourrait lever différentes exceptions selon le type de fichier ou le traitement.
                responses.add("❌ " + originalFilename + " → Erreur lors de la soumission pour ingestion : " + e.getMessage());
            }
        }

        return ResponseEntity.ok(String.join("\n", responses));
    }
}