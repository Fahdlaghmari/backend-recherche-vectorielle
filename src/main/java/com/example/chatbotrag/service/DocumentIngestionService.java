package com.example.chatbotrag.service;

import com.example.chatbotrag.config.Constants;
import com.example.chatbotrag.model.Chunk;
import com.example.chatbotrag.model.Document; // Votre entité JPA Document
import com.example.chatbotrag.model.ProductMetadata;
import com.example.chatbotrag.repository.ProductMetadataRepository;
import com.example.chatbotrag.repository.ChunkRepository;
import com.example.chatbotrag.repository.DocumentRepository;

// Imports pour Apache Tika
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Imports pour l'ancienne méthode PDF (deviennent obsolètes si Tika est utilisé pour tout)
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;


@Service
public class DocumentIngestionService {

    private final ProductMetadataExtractionService metadataExtractionService;
    private final ProductMetadataRepository productMetadataRepository;

    private final ChromaEmbeddingStore embeddingStore;
    private final OllamaClientService ollamaClient;
    private final ChromaHttpClientService chromaHttpClientService;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final LanguageDetectionService languageDetectionService;
    private final SHCodeDocumentParser shCodeParser;

    private static final int CHUNK_SIZE = 1000;
    private static String lastDetectedLanguage = "en"; // Rappel: attention avec la concurrence

    public DocumentIngestionService(
            ChromaEmbeddingStore embeddingStore,
            ChromaHttpClientService chromaHttpClientService,
            OllamaClientService ollamaClient,
            LanguageDetectionService languageDetectionService,
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
SHCodeDocumentParser shCodeParser,
            ProductMetadataExtractionService metadataExtractionService,
            ProductMetadataRepository productMetadataRepository) {

        this.embeddingStore = embeddingStore;
        this.chromaHttpClientService = chromaHttpClientService;
        this.ollamaClient = ollamaClient;
        this.languageDetectionService = languageDetectionService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
this.shCodeParser = shCodeParser;
        this.metadataExtractionService = metadataExtractionService;
        this.productMetadataRepository = productMetadataRepository;
    }

    public static String getLastDetectedLanguage() {
        return lastDetectedLanguage;
    }

    public String ingestDocument(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Le nom du fichier ne peut pas être vide.");
        }

        if (!originalFilename.toLowerCase().endsWith(".pdf") &&
                !originalFilename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("Type de fichier non supporté : " + originalFilename +
                    ". Seuls les fichiers .pdf et .docx sont acceptés.");
        }

        String fullText;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            Tika tika = new Tika();
            try {
                fullText = tika.parseToString(inputStream);
            } catch (TikaException e) {
                System.err.println("Erreur de parsing Tika pour le fichier " + originalFilename + ": " + e.getMessage());
                e.printStackTrace(); // Affiche le stacktrace complet dans les logs backend
                throw new IOException("Erreur lors de l'extraction du texte du fichier: " + e.getMessage(), e);
            }
        }

        if (fullText == null || fullText.trim().isEmpty()) {
            System.out.println("⚠️ Le texte extrait du fichier " + originalFilename + " est vide. L'ingestion est annulée.");
            throw new IllegalArgumentException("Aucun contenu textuel n'a pu être extrait du fichier : " + originalFilename);
        }

        return processTextForIngestion(fullText, originalFilename);
    }

    private String processTextForIngestion(String fullText, String documentName) {
        String documentIdUUID = UUID.randomUUID().toString();
        System.out.println("📄 Ingestion d'un nouveau document : " + documentName + " (UUID de référence pour chunks: " + documentIdUUID + ")");
        
        // Debug: Check if the specific content exists in the full text
        if (fullText.contains("0101292000")) {
            System.out.println("✅ [DEBUG] Trouvé SH code 0101292000 dans le texte complet");
        } else {
            System.out.println("❌ [DEBUG] SH code 0101292000 NOT FOUND dans le texte complet");
        }
        
        if (fullText.contains("de course") && fullText.contains("vingt")) {
            System.out.println("✅ [DEBUG] Trouvé fragments 'de course' et 'vingt' dans le texte");
        } else {
            System.out.println("❌ [DEBUG] Fragments 'de course' et/ou 'vingt' manquants");
        }
        
        System.out.println("📏 [DEBUG] Taille du texte complet: " + fullText.length() + " caractères");
        System.out.println("📝 [DEBUG] Premiers 500 caractères: " + fullText.substring(0, Math.min(500, fullText.length())));

        String lang = languageDetectionService.detectLanguage(fullText);
        lastDetectedLanguage = lang;
        System.out.println("🌐 Langue détectée : " + lang);

        // Use SH code-aware parser for regulatory documents
        List<SHCodeDocumentParser.DocumentChunk> parsedChunks = shCodeParser.parseDocument(fullText, documentName);
        
        // Convert parsed chunks to text chunks for compatibility
        List<String> textChunks = new ArrayList<>();
        for (SHCodeDocumentParser.DocumentChunk parsedChunk : parsedChunks) {
            textChunks.add(parsedChunk.getText());
        }
        
        // Debug: Check chunks for specific content
        System.out.println("📊 [DEBUG] Nombre total de chunks générés: " + textChunks.size());
        for (int i = 0; i < parsedChunks.size(); i++) {
            SHCodeDocumentParser.DocumentChunk parsedChunk = parsedChunks.get(i);
            String chunk = parsedChunk.getText();
            System.out.println("🏷️ [DEBUG] Chunk " + i + " - SH Code: " + parsedChunk.getShCode() + ", Words: " + parsedChunk.getWordCount());
            
            if (chunk.contains("0101292000")) {
                System.out.println("✅ [DEBUG] SH code 0101292000 trouvé dans le chunk " + i);
                System.out.println("📝 [DEBUG] Contenu du chunk " + i + ": " + chunk.substring(0, Math.min(200, chunk.length())) + "...");
                System.out.println("💰 [DEBUG] Métadonnées - DI: " + parsedChunk.getDi() + "%, TPI: " + parsedChunk.getTpi() + "%, TVA: " + parsedChunk.getTva() + "%");
            }
            if (chunk.contains("de course") && chunk.contains("vingt")) {
                System.out.println("✅ [DEBUG] Fragments clés trouvés dans le chunk " + i);
                System.out.println("📝 [DEBUG] Contenu du chunk " + i + ": " + chunk.substring(0, Math.min(200, chunk.length())) + "...");
            }
        }

        Document documentEntity = new Document();
        documentEntity.setName(documentName);
        documentEntity.setLanguage(lang);

        List<Chunk> chunkEntities = new ArrayList<>();
        List<ProductMetadata> metadataList = new ArrayList<>();
        
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);
            String chunkStoreId = documentIdUUID + "_chunk_" + i;
            SHCodeDocumentParser.DocumentChunk parsedChunk = parsedChunks.get(i);

            List<Double> vector = ollamaClient.embed(chunkText);
            if (vector == null || vector.isEmpty()) {
                System.err.println("[WARN] Embedding vide pour le chunk index " + i + " (ID: " + chunkStoreId + "). Chunk ignoré.");
                continue;
            }
            float[] floatArray = new float[vector.size()];
            for (int j = 0; j < vector.size(); j++) {
                floatArray[j] = vector.get(j).floatValue();
            }
            Embedding embedding = new Embedding(floatArray);
            System.out.println("[INFO] Embedding généré pour le chunk " + i + " (ID: " + chunkStoreId + ", taille: " + floatArray.length + ")");
            
            // Store chunk text and embedding in ChromaDB with proper ID
            try {
                chromaHttpClientService.addEmbeddingWithText(Constants.CHROMA_COLLECTION_NAME, chunkStoreId, floatArray, chunkText);
                System.out.println("[INFO] Chunk sauvegardé dans ChromaDB avec ID: " + chunkStoreId);
            } catch (Exception e) {
                System.err.println("[ERROR] Erreur lors de la sauvegarde du chunk " + chunkStoreId + " dans ChromaDB: " + e.getMessage());
                continue; // Skip this chunk and continue with the next one
            }
            
            // Créer l'entité Chunk
            Chunk chunkEntity = new Chunk();
            chunkEntity.setId(chunkStoreId);
            chunkEntity.setText(chunkText);
            chunkEntity.setDocument(documentEntity);
            chunkEntities.add(chunkEntity);
            
            // Préparer les métadonnées pour ce chunk spécifique (sauvegarde différée)
            try {
                ProductMetadata metadata = metadataExtractionService.extractMetadata(parsedChunk);
                metadata.setChunk(chunkEntity);
                metadataList.add(metadata);
                System.out.println("[INFO] Métadonnées préparées pour le chunk " + i + " (SH Code: " + parsedChunk.getShCode() + ")");
            } catch (Exception e) {
                System.err.println("[ERROR] Erreur lors de la préparation des métadonnées pour le chunk " + i + ": " + e.getMessage());
            }
        }

        // Sauvegarder le document avec tous ses chunks
        documentEntity.setChunks(chunkEntities);
        Document savedDocument = documentRepository.save(documentEntity); // L'ID Long du Document est généré ici
        
        // Sauvegarder les métadonnées après que les chunks soient persistés
        for (ProductMetadata metadata : metadataList) {
            try {
                productMetadataRepository.save(metadata);
                System.out.println("[INFO] Métadonnées sauvegardées pour le chunk: " + metadata.getChunk().getId());
            } catch (Exception e) {
                System.err.println("[ERROR] Erreur lors de la sauvegarde des métadonnées: " + e.getMessage());
            }
        }

        System.out.println("✅ Ingestion terminée : " + textChunks.size() + " chunks liés à " + documentName + " (ID BDD Document: " + savedDocument.getId() + ")");

        if (savedDocument.getId() == null) {
            System.err.println("⚠️ L'ID du document (" + documentName + ") sauvegardé est null après l'opération save. Retour de l'UUID de référence comme fallback.");
            return documentIdUUID;
        }
        return savedDocument.getId().toString(); // Retourne l'ID Long auto-généré du Document, converti en String.
    }

    @Deprecated
    public String ingestPdf(File pdfFile, String documentName) throws IOException {
        System.out.println("Appel de la méthode ingestPdf (obsolète) pour : " + documentName);
        String fullText = extractTextFromPdfUsingPdfBox(pdfFile);
        return processTextForIngestion(fullText, documentName);
    }

    private String extractTextFromPdfUsingPdfBox(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private List<String> splitText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int overlap = 200;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end == text.length()) {
                break;
            }
            int nextStart = end - overlap;
            if (nextStart <= start) {
                // Empêche boucle infinie ou start négatif
                start = end;
            } else {
                start = nextStart;
            }
        }
        return chunks;
    }

}