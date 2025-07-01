package com.example.chatbotrag.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent document parser for SH code-based regulatory and trade documents.
 * Chunks documents by SH code sections for optimal RAG retrieval accuracy.
 */
@Service
public class SHCodeDocumentParser {

    private static final int MIN_CHUNK_SIZE = 150; // words
    private static final int MAX_CHUNK_SIZE = 400; // words
    private static final int PREFERRED_CHUNK_SIZE = 300; // words
    
    // Regex patterns for SH code detection
    private static final Pattern SH_CODE_PATTERN = Pattern.compile(
        "(?i)(?:le\\s+)?code\\s+sh\\s+(\\d{10})\\s+correspond\\s+à", 
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    
    // Patterns for metadata extraction
    private static final Pattern DI_PATTERN = Pattern.compile(
        "droit\\s+d[''']importation.*?[:(]?\\s*di\\s*[):]*\\s*(\\d+(?:[.,]\\d+)?)\\s*%", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TPI_PATTERN = Pattern.compile(
        "taxe\\s+parafiscale.*?[:(]?\\s*tpi\\s*[):]*\\s*(\\d+(?:[.,]\\d+)?)\\s*%", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TVA_PATTERN = Pattern.compile(
        "taxe\\s+sur\\s+la\\s+valeur\\s+ajoutée.*?[:(]?\\s*tva\\s*[):]*\\s*(\\d+(?:[.,]\\d+)?)\\s*%", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern CATEGORY_PATTERN = Pattern.compile(
        "catégorie\\s+([^,]+?)(?:,|\\.|$)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern QUOTA_PATTERN = Pattern.compile(
        "(?:contingent|quota).*?(\\d+(?:[.,]\\d+)?)\\s*(?:têtes|unités|tonnes)?", 
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses a document and chunks it by SH code sections
     */
    public List<DocumentChunk> parseDocument(String documentText, String documentName) {
        System.out.println("🔧 [SH_PARSER] Starting SH code-aware parsing for: " + documentName);
        
        // Normalize text
        String normalizedText = normalizeText(documentText);
        
        // Find all SH code positions
        List<SHCodeLocation> shCodeLocations = findSHCodeLocations(normalizedText);
        System.out.println("📊 [SH_PARSER] Found " + shCodeLocations.size() + " SH codes in document");
        
        // Create chunks based on SH code boundaries
        List<DocumentChunk> chunks = createSHCodeChunks(normalizedText, shCodeLocations);
        
        // Post-process chunks (size optimization, metadata extraction)
        chunks = optimizeChunks(chunks);
        
        System.out.println("✅ [SH_PARSER] Generated " + chunks.size() + " optimized chunks");
        
        return chunks;
    }

    /**
     * Normalizes text by fixing encoding issues and whitespace
     */
    private String normalizeText(String text) {
        return text
            .replaceAll("\\s+", " ") // Normalize whitespace
            .replaceAll("Ã©", "é")
            .replaceAll("Ã ", "à")
            .replaceAll("Ã¨", "è")
            .replaceAll("Ã´", "ô")
            .replaceAll("Ã¢", "â")
            .replaceAll("Ã§", "ç")
            .replaceAll("Ãª", "ê")
            .replaceAll("Ã®", "î")
            .replaceAll("Ã¯", "ï")
            .replaceAll("Ã¹", "ù")
            .replaceAll("Ã»", "û")
            .replaceAll("â", "'")
            .replaceAll("â", "–")
            .replaceAll("â", "—")
            .trim();
    }

    /**
     * Finds all SH code locations in the document
     */
    private List<SHCodeLocation> findSHCodeLocations(String text) {
        List<SHCodeLocation> locations = new ArrayList<>();
        Matcher matcher = SH_CODE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String shCode = matcher.group(1);
            int startPos = matcher.start();
            locations.add(new SHCodeLocation(shCode, startPos));
            System.out.println("🎯 [SH_PARSER] Found SH code: " + shCode + " at position " + startPos);
        }
        
        return locations;
    }

    /**
     * Creates chunks based on SH code boundaries
     */
    private List<DocumentChunk> createSHCodeChunks(String text, List<SHCodeLocation> shCodeLocations) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        for (int i = 0; i < shCodeLocations.size(); i++) {
            SHCodeLocation currentLocation = shCodeLocations.get(i);
            int startPos = currentLocation.position;
            
            // Determine end position (next SH code or end of document)
            int endPos = (i + 1 < shCodeLocations.size()) 
                ? shCodeLocations.get(i + 1).position 
                : text.length();
            
            // Extract chunk text
            String chunkText = text.substring(startPos, endPos).trim();
            
            if (!chunkText.isEmpty()) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setText(chunkText);
                chunk.setShCode(currentLocation.shCode);
                chunk.setStartPosition(startPos);
                chunk.setEndPosition(endPos);
                chunk.setWordCount(countWords(chunkText));
                
                // Extract metadata
                extractMetadata(chunk);
                
                chunks.add(chunk);
                System.out.println("📝 [SH_PARSER] Created chunk for SH " + currentLocation.shCode + 
                    " (" + chunk.getWordCount() + " words)");
            }
        }
        
        return chunks;
    }

    /**
     * Optimizes chunks by splitting large ones and merging small ones
     */
    private List<DocumentChunk> optimizeChunks(List<DocumentChunk> chunks) {
        List<DocumentChunk> optimizedChunks = new ArrayList<>();
        
        for (DocumentChunk chunk : chunks) {
            if (chunk.getWordCount() > MAX_CHUNK_SIZE) {
                // Split large chunks
                List<DocumentChunk> splitChunks = splitLargeChunk(chunk);
                optimizedChunks.addAll(splitChunks);
            } else if (chunk.getWordCount() < MIN_CHUNK_SIZE && !optimizedChunks.isEmpty()) {
                // Try to merge with previous chunk if it's from the same SH family
                DocumentChunk previousChunk = optimizedChunks.get(optimizedChunks.size() - 1);
                if (canMergeChunks(previousChunk, chunk)) {
                    mergeChunks(previousChunk, chunk);
                    System.out.println("🔗 [SH_PARSER] Merged small chunk with SH " + chunk.getShCode());
                } else {
                    optimizedChunks.add(chunk);
                }
            } else {
                optimizedChunks.add(chunk);
            }
        }
        
        return optimizedChunks;
    }

    /**
     * Splits a large chunk into smaller semantic units
     */
    private List<DocumentChunk> splitLargeChunk(DocumentChunk largeChunk) {
        List<DocumentChunk> splitChunks = new ArrayList<>();
        String text = largeChunk.getText();
        
        // Split by paragraphs or logical breaks
        String[] paragraphs = text.split("\\n\\s*\\n|\\. (?=[A-Z])|(?<=\\.)\\s+(?=Les mesures|L'importation|importation)");
        
        StringBuilder currentChunk = new StringBuilder();
        String chunkHeader = extractChunkHeader(text);
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;
            
            int potentialWordCount = countWords(currentChunk.toString() + " " + paragraph);
            
            if (potentialWordCount > PREFERRED_CHUNK_SIZE && currentChunk.length() > 0) {
                // Create chunk with current content
                DocumentChunk chunk = createSubChunk(largeChunk, chunkHeader + "\n\n" + currentChunk.toString());
                splitChunks.add(chunk);
                
                // Start new chunk
                currentChunk = new StringBuilder(paragraph);
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            }
        }
        
        // Add remaining content
        if (currentChunk.length() > 0) {
            DocumentChunk chunk = createSubChunk(largeChunk, chunkHeader + "\n\n" + currentChunk.toString());
            splitChunks.add(chunk);
        }
        
        System.out.println("✂️ [SH_PARSER] Split large chunk for SH " + largeChunk.getShCode() + 
            " into " + splitChunks.size() + " smaller chunks");
        
        return splitChunks.isEmpty() ? Arrays.asList(largeChunk) : splitChunks;
    }

    /**
     * Extracts the SH code header from chunk text
     */
    private String extractChunkHeader(String text) {
        String[] lines = text.split("\\n");
        for (String line : lines) {
            if (SH_CODE_PATTERN.matcher(line).find()) {
                return line.trim();
            }
        }
        return "";
    }

    /**
     * Creates a sub-chunk from a parent chunk
     */
    private DocumentChunk createSubChunk(DocumentChunk parent, String text) {
        DocumentChunk subChunk = new DocumentChunk();
        subChunk.setText(text.trim());
        subChunk.setShCode(parent.getShCode());
        subChunk.setWordCount(countWords(text));
        
        // Copy metadata from parent
        subChunk.setProductCategory(parent.getProductCategory());
        subChunk.setProductDescription(parent.getProductDescription());
        subChunk.setDi(parent.getDi());
        subChunk.setTpi(parent.getTpi());
        subChunk.setTva(parent.getTva());
        subChunk.setAgreements(new ArrayList<>(parent.getAgreements()));
        subChunk.setQuota(parent.getQuota());
        
        return subChunk;
    }

    /**
     * Checks if two chunks can be merged
     */
    private boolean canMergeChunks(DocumentChunk chunk1, DocumentChunk chunk2) {
        if (chunk1.getShCode() == null || chunk2.getShCode() == null) {
            return false;
        }
        
        // Can merge if total size is reasonable and they're related
        int combinedSize = chunk1.getWordCount() + chunk2.getWordCount();
        return combinedSize <= MAX_CHUNK_SIZE && 
               (chunk1.getShCode().equals(chunk2.getShCode()) || 
                chunk1.getShCode().substring(0, 6).equals(chunk2.getShCode().substring(0, 6)));
    }

    /**
     * Merges two chunks
     */
    private void mergeChunks(DocumentChunk chunk1, DocumentChunk chunk2) {
        chunk1.setText(chunk1.getText() + "\n\n" + chunk2.getText());
        chunk1.setWordCount(chunk1.getWordCount() + chunk2.getWordCount());
        chunk1.setEndPosition(chunk2.getEndPosition());
        
        // Merge metadata
        if (chunk1.getDi() == null && chunk2.getDi() != null) {
            chunk1.setDi(chunk2.getDi());
        }
        if (chunk1.getTpi() == null && chunk2.getTpi() != null) {
            chunk1.setTpi(chunk2.getTpi());
        }
        if (chunk1.getTva() == null && chunk2.getTva() != null) {
            chunk1.setTva(chunk2.getTva());
        }
        chunk1.getAgreements().addAll(chunk2.getAgreements());
    }

    /**
     * Extracts metadata from chunk text
     */
    private void extractMetadata(DocumentChunk chunk) {
        String text = chunk.getText();
        
        // Extract product category
        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(text);
        if (categoryMatcher.find()) {
            chunk.setProductCategory(categoryMatcher.group(1).trim());
        }
        
        // Extract duties and taxes
        chunk.setDi(extractNumericValue(text, DI_PATTERN));
        chunk.setTpi(extractNumericValue(text, TPI_PATTERN));
        chunk.setTva(extractNumericValue(text, TVA_PATTERN));
        
        // Extract quota information
        Matcher quotaMatcher = QUOTA_PATTERN.matcher(text);
        if (quotaMatcher.find()) {
            chunk.setQuota(quotaMatcher.group(1).replace(",", "."));
        }
        
        // Extract preferential agreements
        chunk.setAgreements(extractAgreements(text));
        
        // Extract product description (more specific than category)
        chunk.setProductDescription(extractProductDescription(text));
    }

    /**
     * Extracts numeric value from text using pattern
     */
    private Double extractNumericValue(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                String value = matcher.group(1).replace(",", ".");
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ [SH_PARSER] Could not parse numeric value: " + matcher.group(1));
            }
        }
        return null;
    }

    /**
     * Extracts preferential agreements and their rates
     */
    private List<String> extractAgreements(String text) {
        List<String> agreements = new ArrayList<>();
        
        // Pattern for agreements
        Pattern agreementPattern = Pattern.compile(
            "importation\\s+([^\\n]+?)\\s+un\\s+droit\\s+préferentiel\\s+de\\s+(\\d+(?:[.,]\\d+)?)\\s*%", 
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = agreementPattern.matcher(text);
        while (matcher.find()) {
            String agreement = matcher.group(1).trim();
            String rate = matcher.group(2).replace(",", ".");
            agreements.add(agreement + ": " + rate + "%");
        }
        
        return agreements;
    }

    /**
     * Extracts detailed product description
     */
    private String extractProductDescription(String text) {
        // Look for text between "Ce code identifie" and next sentence
        Pattern descPattern = Pattern.compile(
            "ce\\s+code\\s+identifie\\s+([^.]+?)(?:\\.|$)", 
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = descPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("^[-–—\\s]+", "");
        }
        
        return null;
    }

    /**
     * Counts words in text
     */
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    /**
     * Inner class to represent SH code locations
     */
    private static class SHCodeLocation {
        String shCode;
        int position;
        
        SHCodeLocation(String shCode, int position) {
            this.shCode = shCode;
            this.position = position;
        }
    }

    /**
     * Document chunk with metadata
     */
    public static class DocumentChunk {
        private String text;
        private String shCode;
        private String productCategory;
        private String productDescription;
        private Double di; // Droit d'Importation
        private Double tpi; // Taxe Parafiscale à l'Importation
        private Double tva; // Taxe sur la Valeur Ajoutée
        private List<String> agreements = new ArrayList<>();
        private String quota;
        private int startPosition;
        private int endPosition;
        private int wordCount;

        // Getters and setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public String getShCode() { return shCode; }
        public void setShCode(String shCode) { this.shCode = shCode; }
        
        public String getProductCategory() { return productCategory; }
        public void setProductCategory(String productCategory) { this.productCategory = productCategory; }
        
        public String getProductDescription() { return productDescription; }
        public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
        
        public Double getDi() { return di; }
        public void setDi(Double di) { this.di = di; }
        
        public Double getTpi() { return tpi; }
        public void setTpi(Double tpi) { this.tpi = tpi; }
        
        public Double getTva() { return tva; }
        public void setTva(Double tva) { this.tva = tva; }
        
        public List<String> getAgreements() { return agreements; }
        public void setAgreements(List<String> agreements) { this.agreements = agreements; }
        
        public String getQuota() { return quota; }
        public void setQuota(String quota) { this.quota = quota; }
        
        public int getStartPosition() { return startPosition; }
        public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
        
        public int getEndPosition() { return endPosition; }
        public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
        
        public int getWordCount() { return wordCount; }
        public void setWordCount(int wordCount) { this.wordCount = wordCount; }
        
        @Override
        public String toString() {
            return String.format("DocumentChunk{shCode='%s', category='%s', words=%d}", 
                shCode, productCategory, wordCount);
        }
    }
}
