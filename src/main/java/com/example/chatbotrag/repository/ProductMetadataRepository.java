package com.example.chatbotrag.repository;

import com.example.chatbotrag.model.ProductMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductMetadataRepository extends JpaRepository<ProductMetadata, Long> {

    // Recherche par code SH
    Optional<ProductMetadata> findByCodeSh(String codeSh);

    // Recherche par type de produit
    List<ProductMetadata> findByTypeProduit(String typeProduit);

    // Recherche par état du produit
    List<ProductMetadata> findByEtatProduit(String etatProduit);

    // Recherche par désossage
    List<ProductMetadata> findByDesossage(String desossage);

    // Recherche combinée type + état
    List<ProductMetadata> findByTypeProduitAndEtatProduit(String typeProduit, String etatProduit);

    // Recherche combinée type + état + désossage
    List<ProductMetadata> findByTypeProduitAndEtatProduitAndDesossage(
            String typeProduit, String etatProduit, String desossage);

    // Recherche par usage spécifique
    List<ProductMetadata> findByUsageSpecifique(String usageSpecifique);

    // Recherche par plage de droits d'importation
    @Query("SELECT pm FROM ProductMetadata pm WHERE pm.droitImportation BETWEEN :min AND :max")
    List<ProductMetadata> findByDroitImportationBetween(@Param("min") Double min, @Param("max") Double max);

    // Recherche textuelle dans la description
    @Query("SELECT pm FROM ProductMetadata pm WHERE LOWER(pm.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<ProductMetadata> findByDescriptionContainingIgnoreCase(@Param("keyword") String keyword);

    // Recherche multi-critères avec JPQL
    @Query("SELECT pm FROM ProductMetadata pm WHERE " +
           "(:typeProduit IS NULL OR pm.typeProduit = :typeProduit) AND " +
           "(:etatProduit IS NULL OR pm.etatProduit = :etatProduit) AND " +
           "(:desossage IS NULL OR pm.desossage = :desossage) AND " +
           "(:usageSpecifique IS NULL OR pm.usageSpecifique = :usageSpecifique)")
    List<ProductMetadata> findByMultipleCriteria(
            @Param("typeProduit") String typeProduit,
            @Param("etatProduit") String etatProduit,
            @Param("desossage") String desossage,
            @Param("usageSpecifique") String usageSpecifique);

    // Recherche de tous les codes SH d'un type de produit
    @Query("SELECT pm.codeSh FROM ProductMetadata pm WHERE pm.typeProduit = :typeProduit")
    List<String> findCodeShByTypeProduit(@Param("typeProduit") String typeProduit);

    // Recherche par similarité de mots-clés normalisés
    @Query("SELECT pm FROM ProductMetadata pm WHERE " +
           "LOWER(pm.motsClesNormalises) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(pm.synonymes) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<ProductMetadata> findByKeywordsOrSynonyms(@Param("keyword") String keyword);

    // Recherche par chunk ID
    @Query("SELECT pm FROM ProductMetadata pm WHERE pm.chunk.id = :chunkId")
    Optional<ProductMetadata> findByChunkId(@Param("chunkId") String chunkId);

    // Recherche de tous les produits avec des préférences tarifaires
    @Query("SELECT pm FROM ProductMetadata pm WHERE pm.preferentiels IS NOT NULL AND pm.preferentiels != ''")
    List<ProductMetadata> findAllWithPreferentialRates();

    // Recherche par codes SH multiples
    @Query("SELECT pm FROM ProductMetadata pm WHERE pm.codeSh IN :codes")
    List<ProductMetadata> findByCodeShIn(@Param("codes") List<String> codes);

    // Statistiques par type de produit
    @Query("SELECT pm.typeProduit, COUNT(pm) FROM ProductMetadata pm GROUP BY pm.typeProduit")
    List<Object[]> countByTypeProduit();

    // Recherche fuzzy par description et mots-clés
    @Query("SELECT pm FROM ProductMetadata pm WHERE " +
           "LOWER(pm.description) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(pm.motsClesNormalises) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(pm.synonymes) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(pm.typeProduit) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(pm.etatProduit) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<ProductMetadata> findByFuzzySearch(@Param("term") String term);

    // Recherche par âge animal
    List<ProductMetadata> findByAgeAnimal(String ageAnimal);

    // Recherche par partie anatomique
    List<ProductMetadata> findByPartieAnatomique(String partieAnatomique);

    // Recherche combinée avec âge et partie anatomique
    List<ProductMetadata> findByAgeAnimalAndPartieAnatomique(String ageAnimal, String partieAnatomique);

    // Recherche par TVA
    List<ProductMetadata> findByTva(Double tva);

    // Recherche par TPI
    List<ProductMetadata> findByTpi(Double tpi);

    // Recherche de tous les produits avec quotas
    @Query("SELECT pm FROM ProductMetadata pm WHERE pm.quotas IS NOT NULL AND pm.quotas != ''")
    List<ProductMetadata> findAllWithQuotas();
}
