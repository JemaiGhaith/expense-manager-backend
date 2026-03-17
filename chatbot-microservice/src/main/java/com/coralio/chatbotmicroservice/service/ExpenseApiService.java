package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.dto.ExpenseNoteDTO;
import com.coralio.chatbotmicroservice.entity.Category;
import com.coralio.chatbotmicroservice.entity.ExpenseLine;
import com.coralio.chatbotmicroservice.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ExpenseApiService {

    @Value("${expense.service.url:http://localhost:8888}")
    private String gatewayUrl;

    private final RestTemplate restTemplate;
    // ✅ Injecter CategoryRepository
    @Autowired
    private CategoryRepository categoryRepository;
    public ExpenseApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Récupère le token JWT de la requête actuelle
     */
    private String getCurrentUserToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader;
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer le token: {}", e.getMessage());
        }

        // Token par défaut pour les tests (à retirer en production)
        log.warn("⚠️ Aucun token trouvé, utilisation du token de test");
        return "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ..."; // Votre token de test
    }

    /**
     * Crée les headers avec le token d'authentification
     */
    private HttpHeaders createHeadersWithAuth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", getCurrentUserToken());
        return headers;
    }

    // Dans ExpenseApiService.java, méthode getNotesByEmployee par exemple
    public List<ExpenseNoteDTO> getNotesByEmployee(String employeeId) {
        try {
            String url = gatewayUrl + "/api/expenses/employee/" + employeeId;

            HttpEntity<?> entity = new HttpEntity<>(createHeadersWithAuth());

            ResponseEntity<List<ExpenseNoteDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<ExpenseNoteDTO>>() {}
            );

            List<ExpenseNoteDTO> notes = response.getBody();

            // Vérification que projectId est bien présent
            if (notes != null && !notes.isEmpty()) {
                notes.forEach(note -> {
                    log.debug("Note #{} - projectId: {}", note.getId(), note.getProjectId());
                });
            }

            return notes != null ? notes : Collections.emptyList();

        } catch (Exception e) {
            log.error("Erreur fetching notes: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public ExpenseNoteDTO getNoteById(Long noteId) {
        try {
            String url = gatewayUrl + "/api/expenses/" + noteId;

            HttpEntity<?> entity = new HttpEntity<>(createHeadersWithAuth());

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ExpenseNoteDTO.class
            );

            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Note non trouvée: {}", noteId);
            return null;
        } catch (Exception e) {
            log.error("Erreur fetching note {}: {}", noteId, e.getMessage());
            return null;
        }
    }

    public List<ExpenseNoteDTO> getNotesByStatus(String status) {
        try {
            String url = gatewayUrl + "/api/expenses/status/" + status;

            HttpEntity<?> entity = new HttpEntity<>(createHeadersWithAuth());

            ResponseEntity<List<ExpenseNoteDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<ExpenseNoteDTO>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Erreur fetching notes by status {}: {}", status, e.getMessage());
            return Collections.emptyList();
        }
    }
    /**
     * Validation par le manager (commentaire obligatoire)
     */
    /**
     * Validation par le manager (commentaire optionnel mais recommandé)
     */
    public boolean managerValidateNote(Long noteId, String comment, String managerId, String managerRole) {
        try {
            String url = gatewayUrl + "/api/expenses/manager/validate/" + noteId;

            HttpHeaders headers = createHeadersWithAuth();

            // ✅ Commentaire par défaut si vide
            String finalComment = (comment == null || comment.trim().isEmpty())
                    ? "Validé sans commentaire"
                    : comment.trim();

            Map<String, String> body = Map.of(
                    "comment", finalComment,
                    "managerId", managerId,
                    "managerName", managerRole
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    entity,
                    ExpenseNoteDTO.class
            );

            log.info("✅ Note {} validée avec succès par manager {}", noteId, managerId);
            return true;

        } catch (HttpClientErrorException.BadRequest e) {
            log.error("❌ Bad request - Vérifiez les paramètres: {}", e.getResponseBodyAsString());
            return false;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("🔒 Non autorisé - Token invalide: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ Erreur validation note {}: {}", noteId, e.getMessage());
            return false;
        }
    }

    /**
     * Rejet par le manager (commentaire OBLIGATOIRE)
     */
    public boolean managerRejectNote(Long noteId, String comment, String managerId, String managerRole) {
        try {
            // ✅ Vérifier que le commentaire n'est pas vide
            if (comment == null || comment.trim().isEmpty()) {
                log.error("❌ Le commentaire est obligatoire pour le rejet");
                return false;
            }

            String url = gatewayUrl + "/api/expenses/manager/reject/" + noteId;

            HttpHeaders headers = createHeadersWithAuth();

            Map<String, String> body = Map.of(
                    "comment", comment.trim(),
                    "managerId", managerId,
                    "managerName", managerRole
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    entity,
                    ExpenseNoteDTO.class
            );

            log.info("✅ Note {} refusée avec succès par manager {}", noteId, managerId);
            return true;

        } catch (HttpClientErrorException.BadRequest e) {
            log.error("❌ Bad request - Le commentaire est obligatoire: {}", e.getResponseBodyAsString());
            return false;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("🔒 Non autorisé - Token invalide: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ Erreur rejet note {}: {}", noteId, e.getMessage());
            return false;
        }
    }

    public boolean validateNote(Long noteId, String comment, String userId, String userRole) {
        try {
            String url = gatewayUrl + "/api/expenses/" + noteId + "/validate";

            HttpHeaders headers = createHeadersWithAuth();
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("comment", comment != null ? comment : "",
                            "userId", userId,
                            "userRole", userRole),
                    headers
            );

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ExpenseNoteDTO.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Erreur validation note {}: {}", noteId, e.getMessage());
            return false;
        }
    }

    public boolean rejectNote(Long noteId, String comment, String userId, String userRole) {
        try {
            String url = gatewayUrl + "/api/expenses/" + noteId + "/reject";

            HttpHeaders headers = createHeadersWithAuth();
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("comment", comment != null ? comment : "",
                            "userId", userId,
                            "userRole", userRole),
                    headers
            );

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ExpenseNoteDTO.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Erreur rejet note {}: {}", noteId, e.getMessage());
            return false;
        }
    }

    public boolean adminRejectNote(Long noteId, String comment) {
        try {
            String url = gatewayUrl + "/api/admin/expenses/" + noteId + "/reject";

            HttpHeaders headers = createHeadersWithAuth();
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("comment", comment != null ? comment : ""),
                    headers
            );

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ExpenseNoteDTO.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Erreur admin reject note {}: {}", noteId, e.getMessage());
            return false;
        }
    }

    public boolean reimburseNote(Long noteId, String comment) {
        try {
            String url = gatewayUrl + "/api/admin/expenses/" + noteId + "/reimburse";

            HttpHeaders headers = createHeadersWithAuth();
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("comment", comment != null ? comment : ""),
                    headers
            );

            ResponseEntity<ExpenseNoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ExpenseNoteDTO.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Erreur reimbursement note {}: {}", noteId, e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getCategories() {
        try {
            String url = gatewayUrl + "/api/categories";

            HttpEntity<?> entity = new HttpEntity<>(createHeadersWithAuth());

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Erreur fetching categories: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    /**
     * Récupère les notes par département (identique à ce que fait le frontend)
     */
    public List<ExpenseNoteDTO> getNotesByDepartment(Long departmentId) {
        try {
            String url = gatewayUrl + "/api/expenses/department/" + departmentId;

            HttpEntity<?> entity = new HttpEntity<>(createHeadersWithAuth());

            ResponseEntity<List<ExpenseNoteDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<ExpenseNoteDTO>>() {}
            );

            List<ExpenseNoteDTO> notes = response.getBody();
            log.info("✅ {} notes récupérées pour le département {}",
                    notes != null ? notes.size() : 0, departmentId);

            return notes != null ? notes : Collections.emptyList();

        } catch (HttpClientErrorException.Unauthorized e) {
            // ✅ Capturer spécifiquement l'erreur 401
            log.error("🔒 Non autorisé (401) - Token invalide ou expiré");
            throw new RuntimeException("UNAUTHORIZED: Votre session a expiré. Veuillez vous reconnecter.");

        } catch (Exception e) {
            log.error("Erreur récupération notes pour département {}: {}", departmentId, e.getMessage());
            throw new RuntimeException("Erreur lors de la récupération des notes: " + e.getMessage());
        }
    }
    /**
     * Récupère les lignes d'une note par son ID
     */
    public List<ExpenseLine> getExpenseLinesByNoteId(Long noteId) {
        try {
            String url = gatewayUrl + "/api/expenses/" + noteId + "/lines";

            HttpEntity<?> entity = new HttpEntity<>(createHeadersWithAuth());

            ResponseEntity<List<ExpenseLine>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<ExpenseLine>>() {}
            );

            List<ExpenseLine> lines = response.getBody();
            log.debug("✅ {} lignes récupérées pour la note {}",
                    lines != null ? lines.size() : 0, noteId);

            return lines != null ? lines : Collections.emptyList();

        } catch (Exception e) {
            log.error("Erreur récupération lignes pour note {}: {}", noteId, e.getMessage());
            return Collections.emptyList();
        }
    }
    /**
     * Récupère le nom d'une catégorie par son ID
     */
    private String getCategoryNameFromRepository(Long categoryId) {
        if (categoryId == null) return "Catégorie inconnue";

        // Vérifier d'abord dans le cache ou utiliser le repository
        Optional<Category> category = categoryRepository.findById(categoryId);
        return category.map(Category::getName).orElse("Catégorie " + categoryId);
    }
}