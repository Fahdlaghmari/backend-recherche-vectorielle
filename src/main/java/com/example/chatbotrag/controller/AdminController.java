package com.example.chatbotrag.controller;

import com.example.chatbotrag.model.AdminUser;
import com.example.chatbotrag.repository.AdminUserRepository;
import com.example.chatbotrag.service.ChromaHttpClientService;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminUserRepository repository;
    private final BCryptPasswordEncoder encoder;
    private final ChromaHttpClientService chromaService;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CHROMA_BASE_URL = "http://localhost:8000/api/v1";

    public AdminController(AdminUserRepository repository, BCryptPasswordEncoder encoder, ChromaHttpClientService chromaService) {
        this.repository = repository;
        this.encoder = encoder;
        this.chromaService = chromaService;
    }

    // ✅ Créer un nouvel admin
    @PostMapping("/create")
    public String createAdmin(@RequestParam String username, @RequestParam String password) {
        Optional<AdminUser> existingUser = repository.findByUsername(username);
        if (existingUser.isPresent()) {

            return "❌ Admin déjà existant : " + username;
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(encoder.encode(password));
        repository.save(admin);

        return "✅ Nouvel admin créé : " + username;
    }

    // ✅ Purger Chroma (supprimer tous les embeddings)
    @GetMapping("/purge-chroma")
    public String purgeChroma() {
        try {
            // Récupérer l'ID de la collection emsi-ai-collection
            String collectionId = getCollectionId("emsi-ai-collection");
            
            // D'abord, récupérer tous les IDs des embeddings
            String getUrl = CHROMA_BASE_URL + "/collections/" + collectionId + "/get";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Récupérer tous les embeddings (sans limite)
            Map<String, Object> getPayload = new HashMap<>();
            
            HttpEntity<Map<String, Object>> getRequest = new HttpEntity<>(getPayload, headers);
            ResponseEntity<Map> getResponse = restTemplate.exchange(getUrl, HttpMethod.POST, getRequest, Map.class);
            
            if (!getResponse.getStatusCode().is2xxSuccessful() || getResponse.getBody() == null) {
                return "❌ Impossible de récupérer les embeddings existants";
            }
            
            // Extraire les IDs des embeddings
            Map<String, Object> responseBody = getResponse.getBody();
            Object idsObj = responseBody.get("ids");
            
            if (!(idsObj instanceof List) || ((List<?>) idsObj).isEmpty()) {
                return "✅ Chroma est déjà vide. Aucun embedding à supprimer.";
            }
            
            List<?> ids = (List<?>) idsObj;
            
            // Maintenant supprimer tous les embeddings par leurs IDs
            String deleteUrl = CHROMA_BASE_URL + "/collections/" + collectionId + "/delete";
            
            Map<String, Object> deletePayload = new HashMap<>();
            deletePayload.put("ids", ids);
            
            HttpEntity<Map<String, Object>> deleteRequest = new HttpEntity<>(deletePayload, headers);
            ResponseEntity<String> deleteResponse = restTemplate.exchange(deleteUrl, HttpMethod.POST, deleteRequest, String.class);
            
            if (deleteResponse.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Purge Chroma réussie: " + ids.size() + " embeddings supprimés");
                return "✅ Chroma purgé avec succès. " + ids.size() + " embeddings supprimés.";
            } else {
                System.err.println("❌ Erreur lors de la suppression: " + deleteResponse.getStatusCode() + " - " + deleteResponse.getBody());
                return "❌ Erreur lors de la suppression: " + deleteResponse.getStatusCode();
            }
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la purge Chroma: " + e.getMessage());
            e.printStackTrace();
            return "❌ Erreur lors de la purge Chroma: " + e.getMessage();
        }
    }

    // ✅ Vérifier le nombre d'embeddings dans Chroma
    @GetMapping("/check-chroma-count")
    public ResponseEntity<Integer> checkChromaCount() {
        try {
            // Récupérer l'ID de la collection emsi-ai-collection
            String collectionId = getCollectionId("emsi-ai-collection");
            
            // Utiliser l'endpoint /get pour récupérer tous les embeddings et compter
            String url = CHROMA_BASE_URL + "/collections/" + collectionId + "/get";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Payload vide pour récupérer tous les embeddings
            Map<String, Object> payload = new HashMap<>();
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // ChromaDB retourne un objet avec des tableaux ids, embeddings, etc.
                Object idsObj = responseBody.get("ids");
                if (idsObj instanceof List) {
                    List<?> ids = (List<?>) idsObj;
                    int count = ids.size();
                    System.out.println("✅ Count Chroma réussi: " + count + " embeddings trouvés");
                    return ResponseEntity.ok(count);
                }
            }
            
            System.out.println("✅ Chroma semble vide ou inaccessible");
            return ResponseEntity.ok(0);
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la vérification du count Chroma: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(0); // Retourner 0 en cas d'erreur
        }
    }

    // Méthode utilitaire pour récupérer l'ID de la collection
    private String getCollectionId(String collectionName) {
        try {
            chromaService.createCollectionIfNotExists(collectionName);
            
            String url = CHROMA_BASE_URL + "/collections/" + collectionName;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            
            if (body == null || body.get("id") == null) {
                throw new RuntimeException("❌ Impossible de récupérer l'ID de la collection : " + collectionName);
            }
            
            return body.get("id").toString();
        } catch (Exception e) {
            throw new RuntimeException("❌ Erreur lors de la récupération de l'ID de collection: " + e.getMessage());
        }
    }
}
