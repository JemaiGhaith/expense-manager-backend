package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.entity.ExpenseNote;
import com.coralio.chatbotmicroservice.repository.ExpenseNoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Base64;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AccessControlService {

    @Autowired
    private ExpenseNoteRepository expenseNoteRepository;

    @Autowired
    private RestTemplate restTemplate;

    // URLs des microservices via Gateway
    private static final String USER_SERVICE_URL = "http://localhost:8888/api/users";
    private static final String EXPENSE_SERVICE_URL = "http://localhost:8888/api/expenses";
    private static final String PROJECT_SERVICE_URL = "http://localhost:8888/api/projects";
    private static final String DEPARTMENT_SERVICE_URL = "http://localhost:8888/api/departments";

    public AccessResult checkNoteAccess(String question, String userId, String userRole, String authToken) {
        Long noteId = extractNoteId(question);

        if (noteId == null) {
            // Pas de note spécifique, accès autorisé
            return new AccessResult(true, null, "Pas de note spécifique", "AUTHORIZED");
        }

        log.info("🔐 Vérification accès à la note #{} pour l'utilisateur {} (rôle: {})",
                noteId, userId, userRole);

        Optional<ExpenseNote> noteOpt = expenseNoteRepository.findById(noteId);

        if (noteOpt.isEmpty()) {
            log.warn("❌ Note #{} n'existe pas", noteId);
            return new AccessResult(false, noteId, "Note #" + noteId + " n'existe pas", "NOT_FOUND");
        }

        ExpenseNote note = noteOpt.get();

        // Vérification selon le rôle
        AccessResult result = null;

        switch (userRole.toUpperCase()) {
            case "EMPLOYEE":
                result = checkEmployeeAccess(note, userId, noteId);
                break;

            case "MANAGER":
                result = checkManagerAccess(note, userId, noteId, authToken);
                break;

            case "ADMIN":
                result = new AccessResult(true, noteId, "Accès autorisé (admin)", "ADMIN_ACCESS");
                break;

            default:
                result = new AccessResult(false, noteId, "Rôle non reconnu: " + userRole, "INVALID_ROLE");
        }

        log.info("🔐 Résultat: {} - {}", result.hasAccess ? "✅" : "❌", result.reason);
        return result;
    }

    private AccessResult checkEmployeeAccess(ExpenseNote note, String userId, Long noteId) {
        if (note.getEmployeeId().equals(userId)) {
            return new AccessResult(true, noteId, "Accès autorisé (propriétaire)", "OWNER_ACCESS");
        } else {
            log.warn("⛔ Employé {} tente d'accéder à la note {} de {}",
                    userId, noteId, note.getEmployeeId());
            return new AccessResult(false, noteId,
                    "Cette note ne vous appartient pas", "NOT_OWNER");
        }
    }

    private AccessResult checkManagerAccess(ExpenseNote note, String managerId, Long noteId, String authToken) {
        String employeeId = note.getEmployeeId();
        Long projectId = note.getProjectId();

        // 1. Extraire le département du token JWT (pas besoin d'appel API !)
        Long managerDepartmentId = extractDepartmentFromToken(authToken);

        if (managerDepartmentId == null) {
            log.warn("⚠️ Manager {} n'a pas de département dans le token", managerId);
            return new AccessResult(false, noteId,
                    "Vous n'avez pas de département assigné dans votre profil",
                    "MANAGER_NO_DEPARTMENT");
        }

        log.info("📋 Manager {} a le département {} (depuis token)", managerId, managerDepartmentId);

        // 2. Récupérer le département du projet
        ProjectInfo projectInfo = getProjectInfo(projectId, authToken);
        if (projectInfo == null) {
            log.warn("⚠️ Projet {} non trouvé", projectId);
            return new AccessResult(false, noteId,
                    "Projet associé à la note non trouvé", "PROJECT_NOT_FOUND");
        }

        Long projectDepartmentId = projectInfo.getDepartmentId();
        log.info("📋 La note #{} est sur le projet {} (département {})",
                noteId, projectId, projectDepartmentId);

        // 3. Vérifier si les départements correspondent
        boolean hasAccess = managerDepartmentId.equals(projectDepartmentId);

        if (hasAccess) {
            log.info("✅ Manager {} a accès à la note #{} (même département: {})",
                    managerId, noteId, managerDepartmentId);
            return new AccessResult(true, noteId,
                    "Accès autorisé (même département)", "MANAGER_DEPARTMENT_ACCESS");
        } else {
            log.warn("⛔ Manager {} (dépt {}) tente d'accéder à une note du département {}",
                    managerId, managerDepartmentId, projectDepartmentId);
            return new AccessResult(false, noteId,
                    "Cette note appartient à un projet d'un autre département", "WRONG_DEPARTMENT");
        }
    }

    /**
     * Extrait le département du token JWT
     */
    private Long extractDepartmentFromToken(String authToken) {
        if (authToken == null || authToken.isEmpty()) {
            log.warn("⚠️ Token manquant");
            return null;
        }

        try {
            // Enlever le préfixe "Bearer " si présent
            String token = authToken.startsWith("Bearer ") ? authToken.substring(7) : authToken;

            // Séparer les parties du token
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("⚠️ Token invalide");
                return null;
            }

            // Décoder la partie payload (deuxième partie)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            log.debug("📦 Payload du token: {}", payload);

            // Parser le JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(payload);

            // Extraire departmentId
            JsonNode deptNode = root.get("departmentId");
            if (deptNode == null) {
                log.warn("⚠️ departmentId non trouvé dans le token");
                return null;
            }

            // Convertir en Long (le token peut contenir une chaîne ou un nombre)
            Long departmentId = deptNode.isNumber() ?
                    deptNode.asLong() :
                    Long.parseLong(deptNode.asText());

            log.info("✅ DepartmentId extrait du token: {}", departmentId);
            return departmentId;

        } catch (Exception e) {
            log.error("❌ Erreur lors de l'extraction du département du token: {}", e.getMessage());
            return null;
        }
    }
    /**
     * Récupère les informations du manager depuis Keycloak
     */
    private ManagerInfo getManagerInfo(String managerId, String authToken) {
        try {
            String url = USER_SERVICE_URL + "/" + managerId;
            // Ajouter le token dans les headers
            ManagerInfo manager = restTemplate.getForObject(url, ManagerInfo.class);
            log.debug("📦 Manager info: {}", manager);
            return manager;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Manager {} non trouvé", managerId);
            return null;
        } catch (Exception e) {
            log.error("Erreur récupération manager {}: {}", managerId, e.getMessage());
            return null;
        }
    }

    /**
     * Récupère les informations du projet depuis le microservice expense
     */
    private ProjectInfo getProjectInfo(Long projectId, String authToken) {
        try {
            // Utiliser l'endpoint public pour les managers
            String url = PROJECT_SERVICE_URL + "/public/" + projectId;

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + authToken);
            // Ajouter le rôle pour indiquer que c'est un manager
            headers.set("X-User-Role", "MANAGER");

            org.springframework.http.HttpEntity<?> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<ProjectInfo> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    ProjectInfo.class
            );

            ProjectInfo project = response.getBody();
            log.info("📦 Projet {}: département={}", projectId,
                    project != null ? project.getDepartmentId() : null);
            return project;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Projet {} non trouvé", projectId);
            return null;
        } catch (Exception e) {
            log.error("Erreur récupération projet {}: {}", projectId, e.getMessage());
            return null;
        }
    }

    private Long extractNoteId(String question) {
        if (question == null) return null;

        Pattern[] patterns = {
                Pattern.compile("note\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("#(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("n°\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("num[ée]ro\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(question);
            if (matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.warn("Format d'ID invalide: {}", matcher.group(1));
                }
            }
        }
        return null;
    }

    // ==================== DTOs INTERNES ====================

    public static class AccessResult {
        public final boolean hasAccess;
        public final Long noteId;
        public final String reason;
        public final String code;

        public AccessResult(boolean hasAccess, Long noteId, String reason, String code) {
            this.hasAccess = hasAccess;
            this.noteId = noteId;
            this.reason = reason;
            this.code = code;
        }
    }

    public static class ManagerInfo {
        private String id;
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private Long departmentId;
        private List<String> roles;

        // Getters et setters
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }

        @Override
        public String toString() {
            return "ManagerInfo{id=" + id + ", departmentId=" + departmentId + "}";
        }
    }

    public static class ProjectInfo {
        private Long id;
        private String name;
        private String code;
        private Long departmentId;
        private String status;

        // Getters et setters
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

        @Override
        public String toString() {
            return "ProjectInfo{id=" + id + ", departmentId=" + departmentId + "}";
        }
    }
}