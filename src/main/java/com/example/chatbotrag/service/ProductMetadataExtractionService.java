package com.example.chatbotrag.service;

import com.example.chatbotrag.model.ProductMetadata;
import com.example.chatbotrag.service.SHCodeDocumentParser.DocumentChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Service pour extraire et normaliser les métadonnées des produits alimentaires
 * à partir des chunks de documents réglementaires
 */
@Service
public class ProductMetadataExtractionService {

    private final ObjectMapper objectMapper;
    
    // Patterns pour l'extraction intelligente
    private static final Pattern MEAT_TYPE_PATTERN = Pattern.compile(
        "(?i)viande?s?\\s+(bovine|porcine|ovine|caprine|de\\s+porc|de\\s+bœuf|de\\s+mouton|de\\s+chèvre)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern STATE_PATTERN = Pattern.compile(
        "(?i)(fraîche?s?|congelée?s?|réfrigérée?s?|surgelée?s?)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern BONE_PATTERN = Pattern.compile(
        "(?i)(désossée?s?|non\\s+désossée?s?|avec\\s+os|sans\\s+os)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern AGE_PATTERN = Pattern.compile(
        "(?i)(moins\\s+de\\s+six\\s+mois|âgés?\\s+de\\s+moins\\s+de\\s+\\d+\\s+mois|plus\\s+de\\s+\\d+\\s+mois|\\d+\\s+à\\s+\\d+\\s+mois)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ANATOMICAL_PART_PATTERN = Pattern.compile(
        "(?i)(carcasse?s?|demi.carcasse?s?|quartier?s?|pièce?s?|morceaux?)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SPECIFIC_USE_PATTERN = Pattern.compile(
        "(?i)(course|reproduction|parcs\\s+zoologiques|destinés?\\s+aux\\s+parcs)", 
        Pattern.CASE_INSENSITIVE
    );

    // Dictionnaire de normalisation des termes
    private static final Map<String, String> NORMALIZATION_MAP = new HashMap<>();
    static {
        // Types de viande
        NORMALIZATION_MAP.put("bovine", "bovine");
        NORMALIZATION_MAP.put("porcine", "porcine");
        NORMALIZATION_MAP.put("de porc", "porcine");
        NORMALIZATION_MAP.put("ovine", "ovine");
        NORMALIZATION_MAP.put("de mouton", "ovine");
        NORMALIZATION_MAP.put("caprine", "caprine");
        NORMALIZATION_MAP.put("de chèvre", "caprine");
        NORMALIZATION_MAP.put("de bœuf", "bovine");
        
        // États
        NORMALIZATION_MAP.put("fraîche", "frais");
        NORMALIZATION_MAP.put("fraîches", "frais");
        NORMALIZATION_MAP.put("congelée", "congele");
        NORMALIZATION_MAP.put("congelées", "congele");
        NORMALIZATION_MAP.put("réfrigérée", "refrigere");
        NORMALIZATION_MAP.put("réfrigérées", "refrigere");
        NORMALIZATION_MAP.put("surgelée", "congele");
        NORMALIZATION_MAP.put("surgelées", "congele");
        
        // Désossage
        NORMALIZATION_MAP.put("désossée", "desossee");
        NORMALIZATION_MAP.put("désossées", "desossee");
        NORMALIZATION_MAP.put("non désossée", "non_desossee");
        NORMALIZATION_MAP.put("non désossées", "non_desossee");
        NORMALIZATION_MAP.put("avec os", "avec_os");
        NORMALIZATION_MAP.put("sans os", "sans_os");
        
        // Parties anatomiques
        NORMALIZATION_MAP.put("carcasse", "carcasse");
        NORMALIZATION_MAP.put("carcasses", "carcasse");
        NORMALIZATION_MAP.put("demi-carcasse", "demi_carcasse");
        NORMALIZATION_MAP.put("demi-carcasses", "demi_carcasse");
        NORMALIZATION_MAP.put("quartier", "quartier");
        NORMALIZATION_MAP.put("quartiers", "quartier");
        NORMALIZATION_MAP.put("pièce", "pieces");
        NORMALIZATION_MAP.put("pièces", "pieces");
        NORMALIZATION_MAP.put("morceaux", "pieces");
        
        // Usages spécifiques
        NORMALIZATION_MAP.put("course", "course");
        NORMALIZATION_MAP.put("reproduction", "reproduction");
        NORMALIZATION_MAP.put("parcs zoologiques", "parcs_zoologiques");
        NORMALIZATION_MAP.put("destinés aux parcs", "parcs_zoologiques");
    }

    // Dictionnaire de synonymes pour améliorer la recherche
    private static final Map<String, List<String>> SYNONYMS_MAP = new HashMap<>();
    static {
        SYNONYMS_MAP.put("bovine", Arrays.asList("bœuf", "vache", "bovin", "taureau", "veau"));
        SYNONYMS_MAP.put("porcine", Arrays.asList("porc", "cochon", "porcin", "sanglier"));
        SYNONYMS_MAP.put("ovine", Arrays.asList("mouton", "brebis", "agneau", "ovin"));
        SYNONYMS_MAP.put("caprine", Arrays.asList("chèvre", "bouc", "caprin", "chevreau"));
        SYNONYMS_MAP.put("congele", Arrays.asList("surgelé", "gelé", "glacé", "frozen"));
        SYNONYMS_MAP.put("frais", Arrays.asList("réfrigéré", "fresh", "non congelé"));
        SYNONYMS_MAP.put("desossee", Arrays.asList("sans os", "désossé", "boneless"));
        SYNONYMS_MAP.put("non_desossee", Arrays.asList("avec os", "non désossé", "bone-in"));
        SYNONYMS_MAP.put("course", Arrays.asList("racing", "compétition", "hippique"));
        SYNONYMS_MAP.put("parcs_zoologiques", Arrays.asList("zoo", "zoologique", "parc animalier"));
    }

    public ProductMetadataExtractionService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extrait les métadonnées d'un DocumentChunk du parser SH
     */
    public ProductMetadata extractMetadata(DocumentChunk chunk) {
        System.out.println("🔍 [METADATA_EXTRACTION] Début extraction pour SH: " + chunk.getShCode());
        
        ProductMetadata metadata = new ProductMetadata();
        metadata.setCodeSh(chunk.getShCode());
        metadata.setDescription(chunk.getProductDescription() != null ? 
                                chunk.getProductDescription() : chunk.getText());
        
        // Extraction des métadonnées des droits de douane
        metadata.setDroitImportation(chunk.getDi());
        metadata.setTpi(chunk.getTpi());
        metadata.setTva(chunk.getTva());
        metadata.setQuotas(chunk.getQuota());
        
        // Conversion des accords préférentiels en JSON
        if (chunk.getAgreements() != null && !chunk.getAgreements().isEmpty()) {
            try {
                metadata.setPreferentiels(objectMapper.writeValueAsString(chunk.getAgreements()));
            } catch (JsonProcessingException e) {
                System.err.println("⚠️ [METADATA_EXTRACTION] Erreur serialisation JSON: " + e.getMessage());
                metadata.setPreferentiels(String.join(", ", chunk.getAgreements()));
            }
        }
        
        // Extraction intelligente des métadonnées normalisées
        String textToAnalyze = chunk.getText();
        
        // Type de produit
        String typeProduit = extractAndNormalize(textToAnalyze, MEAT_TYPE_PATTERN);
        metadata.setTypeProduit(typeProduit);
        
        // État du produit
        String etatProduit = extractAndNormalize(textToAnalyze, STATE_PATTERN);
        metadata.setEtatProduit(etatProduit);
        
        // Désossage
        String desossage = extractAndNormalize(textToAnalyze, BONE_PATTERN);
        metadata.setDesossage(desossage);
        
        // Âge animal
        String ageAnimal = extractAndNormalizeAge(textToAnalyze);
        metadata.setAgeAnimal(ageAnimal);
        
        // Partie anatomique
        String partieAnatomique = extractAndNormalize(textToAnalyze, ANATOMICAL_PART_PATTERN);
        metadata.setPartieAnatomique(partieAnatomique);
        
        // Usage spécifique
        String usageSpecifique = extractAndNormalize(textToAnalyze, SPECIFIC_USE_PATTERN);
        metadata.setUsageSpecifique(usageSpecifique);
        
        // Génération des mots-clés normalisés
        metadata.setMotsClesNormalises(generateNormalizedKeywords(metadata));
        
        // Génération des synonymes
        metadata.setSynonymes(generateSynonyms(metadata));
        
        System.out.println("✅ [METADATA_EXTRACTION] Métadonnées extraites: " + metadata.toString());
        return metadata;
    }

    /**
     * Extrait et normalise un terme selon un pattern donné
     */
    private String extractAndNormalize(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String extracted = matcher.group().trim().toLowerCase();
            
            // Recherche de normalisation
            for (Map.Entry<String, String> entry : NORMALIZATION_MAP.entrySet()) {
                if (extracted.contains(entry.getKey())) {
                    System.out.println("🔄 [NORMALIZATION] '" + extracted + "' -> '" + entry.getValue() + "'");
                    return entry.getValue();
                }
            }
            
            // Si pas de normalisation trouvée, retourner le terme nettoyé
            return extracted.replaceAll("\\s+", "_");
        }
        return null;
    }

    /**
     * Extraction spécialisée pour l'âge des animaux
     */
    private String extractAndNormalizeAge(String text) {
        Matcher matcher = AGE_PATTERN.matcher(text);
        if (matcher.find()) {
            String ageText = matcher.group().trim().toLowerCase();
            
            // Normalisation spécifique pour l'âge
            if (ageText.contains("moins de six mois") || ageText.contains("moins de 6 mois")) {
                return "moins_6_mois";
            } else if (ageText.contains("six") && ageText.contains("vingt")) {
                return "6_20_mois";
            } else if (ageText.contains("plus de 20 mois") || ageText.contains("plus de vingt mois")) {
                return "plus_20_mois";
            }
            
            // Extraction générique pour d'autres patterns d'âge
            return ageText.replaceAll("\\s+", "_");
        }
        return null;
    }

    /**
     * Génère des mots-clés normalisés pour la recherche
     */
    private String generateNormalizedKeywords(ProductMetadata metadata) {
        Set<String> keywords = new HashSet<>();
        
        // Ajout des métadonnées normalisées
        if (metadata.getTypeProduit() != null) {
            keywords.add(metadata.getTypeProduit());
            keywords.add("viande " + metadata.getTypeProduit());
        }
        
        if (metadata.getEtatProduit() != null) {
            keywords.add(metadata.getEtatProduit());
        }
        
        if (metadata.getDesossage() != null) {
            keywords.add(metadata.getDesossage());
        }
        
        if (metadata.getAgeAnimal() != null) {
            keywords.add(metadata.getAgeAnimal());
        }
        
        if (metadata.getPartieAnatomique() != null) {
            keywords.add(metadata.getPartieAnatomique());
        }
        
        if (metadata.getUsageSpecifique() != null) {
            keywords.add(metadata.getUsageSpecifique());
        }
        
        // Mots-clés tarifaires
        if (metadata.getDroitImportation() != null) {
            keywords.add("droit_importation_" + metadata.getDroitImportation().toString().replace(".", "_"));
        }
        
        // Mots-clés génériques
        keywords.add("importation");
        keywords.add("produit_alimentaire");
        keywords.add("code_sh_" + metadata.getCodeSh());
        
        return String.join(" ", keywords);
    }

    /**
     * Génère des synonymes pour améliorer la recherche
     */
    private String generateSynonyms(ProductMetadata metadata) {
        Set<String> synonyms = new HashSet<>();
        
        // Synonymes pour le type de produit
        if (metadata.getTypeProduit() != null && SYNONYMS_MAP.containsKey(metadata.getTypeProduit())) {
            synonyms.addAll(SYNONYMS_MAP.get(metadata.getTypeProduit()));
        }
        
        // Synonymes pour l'état
        if (metadata.getEtatProduit() != null && SYNONYMS_MAP.containsKey(metadata.getEtatProduit())) {
            synonyms.addAll(SYNONYMS_MAP.get(metadata.getEtatProduit()));
        }
        
        // Synonymes pour le désossage
        if (metadata.getDesossage() != null && SYNONYMS_MAP.containsKey(metadata.getDesossage())) {
            synonyms.addAll(SYNONYMS_MAP.get(metadata.getDesossage()));
        }
        
        // Synonymes pour l'usage spécifique
        if (metadata.getUsageSpecifique() != null && SYNONYMS_MAP.containsKey(metadata.getUsageSpecifique())) {
            synonyms.addAll(SYNONYMS_MAP.get(metadata.getUsageSpecifique()));
        }
        
        return String.join(" ", synonyms);
    }

    /**
     * Méthode utilitaire pour extraire les métadonnées d'une requête utilisateur
     */
    public Map<String, String> extractQueryMetadata(String userQuery) {
        Map<String, String> queryMetadata = new HashMap<>();
        String queryLower = userQuery.toLowerCase();
        
        // Extraction du type de produit
        Matcher typeMatcher = MEAT_TYPE_PATTERN.matcher(queryLower);
        if (typeMatcher.find()) {
            String type = extractAndNormalize(queryLower, MEAT_TYPE_PATTERN);
            queryMetadata.put("type", type);
        }
        
        // Extraction de l'état
        Matcher stateMatcher = STATE_PATTERN.matcher(queryLower);
        if (stateMatcher.find()) {
            String state = extractAndNormalize(queryLower, STATE_PATTERN);
            queryMetadata.put("etat", state);
        }
        
        // Extraction du désossage
        Matcher boneMatcher = BONE_PATTERN.matcher(queryLower);
        if (boneMatcher.find()) {
            String bone = extractAndNormalize(queryLower, BONE_PATTERN);
            queryMetadata.put("desossage", bone);
        }
        
        // Extraction de l'âge
        String age = extractAndNormalizeAge(queryLower);
        if (age != null) {
            queryMetadata.put("age", age);
        }
        
        // Extraction de l'usage spécifique
        Matcher usageMatcher = SPECIFIC_USE_PATTERN.matcher(queryLower);
        if (usageMatcher.find()) {
            String usage = extractAndNormalize(queryLower, SPECIFIC_USE_PATTERN);
            queryMetadata.put("usage", usage);
        }
        
        System.out.println("🔍 [QUERY_METADATA] Métadonnées extraites de la requête: " + queryMetadata);
        return queryMetadata;
    }

    /**
     * Normalise un terme selon le dictionnaire de normalisation
     */
    public String normalizeTermWithDictionary(String term) {
        if (term == null) return null;
        
        String lowerTerm = term.toLowerCase();
        
        // Recherche exacte
        if (NORMALIZATION_MAP.containsKey(lowerTerm)) {
            return NORMALIZATION_MAP.get(lowerTerm);
        }
        
        // Recherche de correspondance partielle
        for (Map.Entry<String, String> entry : NORMALIZATION_MAP.entrySet()) {
            if (lowerTerm.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Pas de correspondance trouvée
        return lowerTerm.replaceAll("\\s+", "_");
    }

    /**
     * Trouve les synonymes d'un terme
     */
    public List<String> findSynonyms(String term) {
        if (term == null) return new ArrayList<>();
        
        String normalizedTerm = normalizeTermWithDictionary(term);
        if (SYNONYMS_MAP.containsKey(normalizedTerm)) {
            return new ArrayList<>(SYNONYMS_MAP.get(normalizedTerm));
        }
        
        return new ArrayList<>();
    }
}
