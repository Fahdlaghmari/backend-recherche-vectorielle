package com.example.chatbotrag.model;

import jakarta.persistence.*;
import java.util.Map;
import java.util.HashMap;

/**
 * Entité JPA pour stocker les métadonnées structurées des produits alimentaires
 * pour améliorer la précision du RAG avec recherche vectorielle + filtrage
 */
@Entity
@Table(name = "product_metadata")
public class ProductMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_sh", nullable = false, unique = true, length = 10)
    private String codeSh;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Métadonnées normalisées pour filtrage
    @Column(name = "type_produit", length = 50)
    private String typeProduit; // "bovine", "porcine", "ovine", "caprine", "volaille", etc.

    @Column(name = "etat_produit", length = 20)
    private String etatProduit; // "frais", "congele", "refrigere"

    @Column(name = "desossage", length = 20)
    private String desossage; // "desossee", "non_desossee", "avec_os", "sans_os"

    @Column(name = "age_animal", length = 50)
    private String ageAnimal; // "moins_6_mois", "6_20_mois", "plus_20_mois", etc.

    @Column(name = "partie_anatomique", length = 100)
    private String partieAnatomique; // "carcasse", "demi_carcasse", "quartier", "pieces", etc.

    @Column(name = "usage_specifique", length = 100)
    private String usageSpecifique; // "course", "reproduction", "parcs_zoologiques", etc.

    // Droits de douane
    @Column(name = "droit_importation")
    private Double droitImportation;

    @Column(name = "tpi")
    private Double tpi;

    @Column(name = "tva")
    private Double tva;

    // Préférences tarifaires (stockées au format JSON)
    @Column(name = "preferentiels", columnDefinition = "JSON")
    private String preferentiels;

    @Column(name = "quotas", columnDefinition = "TEXT")
    private String quotas;

    // Relation avec le chunk original
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", unique = true)
    private Chunk chunk;

    // Métadonnées additionnelles pour recherche sémantique
    @Column(name = "mots_cles_normalises", columnDefinition = "TEXT")
    private String motsClesNormalises;

    @Column(name = "synonymes", columnDefinition = "TEXT")
    private String synonymes;

    // Constructeurs
    public ProductMetadata() {}

    public ProductMetadata(String codeSh, String description) {
        this.codeSh = codeSh;
        this.description = description;
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodeSh() {
        return codeSh;
    }

    public void setCodeSh(String codeSh) {
        this.codeSh = codeSh;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTypeProduit() {
        return typeProduit;
    }

    public void setTypeProduit(String typeProduit) {
        this.typeProduit = typeProduit;
    }

    public String getEtatProduit() {
        return etatProduit;
    }

    public void setEtatProduit(String etatProduit) {
        this.etatProduit = etatProduit;
    }

    public String getDesossage() {
        return desossage;
    }

    public void setDesossage(String desossage) {
        this.desossage = desossage;
    }

    public String getAgeAnimal() {
        return ageAnimal;
    }

    public void setAgeAnimal(String ageAnimal) {
        this.ageAnimal = ageAnimal;
    }

    public String getPartieAnatomique() {
        return partieAnatomique;
    }

    public void setPartieAnatomique(String partieAnatomique) {
        this.partieAnatomique = partieAnatomique;
    }

    public String getUsageSpecifique() {
        return usageSpecifique;
    }

    public void setUsageSpecifique(String usageSpecifique) {
        this.usageSpecifique = usageSpecifique;
    }

    public Double getDroitImportation() {
        return droitImportation;
    }

    public void setDroitImportation(Double droitImportation) {
        this.droitImportation = droitImportation;
    }

    public Double getTpi() {
        return tpi;
    }

    public void setTpi(Double tpi) {
        this.tpi = tpi;
    }

    public Double getTva() {
        return tva;
    }

    public void setTva(Double tva) {
        this.tva = tva;
    }

    public String getPreferentiels() {
        return preferentiels;
    }

    public void setPreferentiels(String preferentiels) {
        this.preferentiels = preferentiels;
    }

    public String getQuotas() {
        return quotas;
    }

    public void setQuotas(String quotas) {
        this.quotas = quotas;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public String getMotsClesNormalises() {
        return motsClesNormalises;
    }

    public void setMotsClesNormalises(String motsClesNormalises) {
        this.motsClesNormalises = motsClesNormalises;
    }

    public String getSynonymes() {
        return synonymes;
    }

    public void setSynonymes(String synonymes) {
        this.synonymes = synonymes;
    }

    /**
     * Génère une représentation textuelle optimisée pour la recherche vectorielle
     */
    public String toSearchableText() {
        StringBuilder sb = new StringBuilder();
        
        // Code SH
        if (codeSh != null) {
            sb.append("Code SH: ").append(codeSh).append(". ");
        }
        
        // Description principale
        if (description != null) {
            sb.append(description).append(". ");
        }
        
        // Métadonnées normalisées
        if (typeProduit != null) {
            sb.append("Type: ").append(typeProduit.replace("_", " ")).append(". ");
        }
        
        if (etatProduit != null) {
            sb.append("État: ").append(etatProduit.replace("_", " ")).append(". ");
        }
        
        if (desossage != null) {
            sb.append("Désossage: ").append(desossage.replace("_", " ")).append(". ");
        }
        
        if (ageAnimal != null) {
            sb.append("Âge: ").append(ageAnimal.replace("_", " ")).append(". ");
        }
        
        if (partieAnatomique != null) {
            sb.append("Partie: ").append(partieAnatomique.replace("_", " ")).append(". ");
        }
        
        if (usageSpecifique != null) {
            sb.append("Usage: ").append(usageSpecifique.replace("_", " ")).append(". ");
        }
        
        // Informations tarifaires
        if (droitImportation != null) {
            sb.append("Droit d'importation: ").append(droitImportation).append("%. ");
        }
        
        if (tpi != null) {
            sb.append("TPI: ").append(tpi).append("%. ");
        }
        
        if (tva != null) {
            sb.append("TVA: ").append(tva).append("%. ");
        }
        
        // Mots-clés normalisés
        if (motsClesNormalises != null) {
            sb.append(motsClesNormalises).append(". ");
        }
        
        // Synonymes
        if (synonymes != null) {
            sb.append(synonymes).append(". ");
        }
        
        return sb.toString().trim();
    }

    /**
     * Calcule un score de similarité avec une requête basé sur les métadonnées
     */
    public double calculateMetadataScore(Map<String, String> queryMetadata) {
        double score = 0.0;
        int maxScore = 0;
        
        // Score pour type de produit (poids: 3)
        if (queryMetadata.containsKey("type") && typeProduit != null) {
            maxScore += 3;
            if (typeProduit.toLowerCase().contains(queryMetadata.get("type").toLowerCase())) {
                score += 3;
            }
        }
        
        // Score pour état du produit (poids: 2)
        if (queryMetadata.containsKey("etat") && etatProduit != null) {
            maxScore += 2;
            if (etatProduit.toLowerCase().contains(queryMetadata.get("etat").toLowerCase())) {
                score += 2;
            }
        }
        
        // Score pour désossage (poids: 2)
        if (queryMetadata.containsKey("desossage") && desossage != null) {
            maxScore += 2;
            if (desossage.toLowerCase().contains(queryMetadata.get("desossage").toLowerCase())) {
                score += 2;
            }
        }
        
        // Score pour âge (poids: 1)
        if (queryMetadata.containsKey("age") && ageAnimal != null) {
            maxScore += 1;
            if (ageAnimal.toLowerCase().contains(queryMetadata.get("age").toLowerCase())) {
                score += 1;
            }
        }
        
        // Score pour usage spécifique (poids: 2)
        if (queryMetadata.containsKey("usage") && usageSpecifique != null) {
            maxScore += 2;
            if (usageSpecifique.toLowerCase().contains(queryMetadata.get("usage").toLowerCase())) {
                score += 2;
            }
        }
        
        return maxScore > 0 ? score / maxScore : 0.0;
    }

    @Override
    public String toString() {
        return "ProductMetadata{" +
                "id=" + id +
                ", codeSh='" + codeSh + '\'' +
                ", typeProduit='" + typeProduit + '\'' +
                ", etatProduit='" + etatProduit + '\'' +
                ", desossage='" + desossage + '\'' +
                ", droitImportation=" + droitImportation +
                '}';
    }
}
