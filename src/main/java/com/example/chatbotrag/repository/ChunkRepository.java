package com.example.chatbotrag.repository;

import com.example.chatbotrag.model.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, String> {
    // Recherche d'un chunk contenant un mot-clé (compatible CLOB/TEXT)
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM chunk WHERE LOWER(text) LIKE LOWER(CONCAT('%', :keyword, '%')) LIMIT 1", nativeQuery = true)
    Optional<Chunk> findFirstByKeyword(@org.springframework.data.repository.query.Param("keyword") String keyword);

    // Recherche d'un chunk contenant à la fois un mot-clé produit et un code SH
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM chunk WHERE LOWER(text) LIKE LOWER(CONCAT('%', :keyword, '%')) AND text LIKE CONCAT('%', :codeSh, '%') LIMIT 5", nativeQuery = true)
    java.util.List<Chunk> findByKeywordAndCodeSh(@org.springframework.data.repository.query.Param("keyword") String keyword, @org.springframework.data.repository.query.Param("codeSh") String codeSh);


    // Récupère les N premiers chunks pour limiter la mémoire
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM chunk LIMIT :limit", nativeQuery = true)
    java.util.List<Chunk> findTopN(@org.springframework.data.repository.query.Param("limit") int limit);
}
