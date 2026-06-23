package com.coralio.user_microservice.controllers;

import com.coralio.user_microservice.dto.PasswordChangeDTO;
import com.coralio.user_microservice.dto.ProfileUpdateDTO;
import com.coralio.user_microservice.dto.UserCreateDTO;
import com.coralio.user_microservice.dto.UserUpdateDTO;
import com.coralio.user_microservice.services.KeycloakAdminClient;
import com.coralio.user_microservice.services.DepartmentService;
import com.coralio.user_microservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;  // ✅ AJOUTER CET IMPORT

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j  // ✅ AJOUTER CETTE ANNOTATION
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final KeycloakAdminClient keycloakClient;
    private final DepartmentService departmentService;
    private final UserService userService;  // ✅ AJOUTER CETTE LIGNE

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable("id") String id) {
        UserRepresentation user = keycloakClient.getUserById(id);

        String departmentId = extractAttribute(user, "departmentId");
        String departmentName = null;

        if (departmentId != null) {
            try {
                departmentName = departmentService.getDepartmentName(Long.parseLong(departmentId));
            } catch (Exception e) {
                // Ignorer si département non trouvé
            }
        }

        List<String> roles = extractRoles(user);

        return ResponseEntity.ok(
                new UserDto(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getUsername(),
                        user.getEmail(),
                        departmentId,
                        departmentName,
                        user.isEnabled(),
                        user.isEmailVerified(),  // ✅ AJOUTÉ
                        roles
                )
        );
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserRepresentation> users = keycloakClient.getAllUsers();

        List<UserDto> userDtos = users.stream()
                .map(user -> {
                    String departmentId = extractAttribute(user, "departmentId");
                    String departmentName = null;

                    if (departmentId != null) {
                        try {
                            departmentName = departmentService.getDepartmentName(Long.parseLong(departmentId));
                        } catch (Exception e) {
                            // Ignorer si département non trouvé
                        }
                    }

                    List<String> roles = extractRoles(user);

                    return new UserDto(
                            user.getId(),
                            user.getFirstName(),
                            user.getLastName(),
                            user.getUsername(),
                            user.getEmail(),
                            departmentId,
                            departmentName,
                            user.isEnabled(),
                            user.isEmailVerified(),  // ✅ AJOUTÉ
                            roles
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(userDtos);
    }

    private String extractAttribute(UserRepresentation user, String attributeName) {
        if (user.getAttributes() != null && user.getAttributes().containsKey(attributeName)) {
            List<String> values = user.getAttributes().get(attributeName);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }

    private List<String> extractRoles(UserRepresentation user) {
        try {
            return keycloakClient.getUserRoles(user.getId());
        } catch (Exception e) {
            log.error("❌ Erreur extraction rôles: {}", e.getMessage());
            return List.of();
        }
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserCreateDTO userDTO) {
        try {
            if (userDTO.getUsername() == null || userDTO.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Le nom d'utilisateur est requis"));
            }
            if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "L'email est requis"));
            }
            if (userDTO.getPassword() == null || userDTO.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Le mot de passe doit contenir au moins 6 caractères"));
            }

            String userId = keycloakClient.createUser(
                    userDTO.getUsername(),
                    userDTO.getEmail(),
                    userDTO.getFirstName(),
                    userDTO.getLastName(),
                    userDTO.getPassword(),
                    userDTO.isEnabled(),
                    userDTO.isEmailVerified(),
                    userDTO.getAttributes(),
                    userDTO.getRealmRoles()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("id", userId);
            response.put("message", "Utilisateur créé avec succès");
            response.put("username", userDTO.getUsername());
            response.put("enabled", userDTO.isEnabled());
            response.put("emailVerified", userDTO.isEmailVerified());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody UserUpdateDTO userDTO) {
        try {
            if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "L'email est requis"));
            }

            UserDto updatedUser = keycloakClient.updateUser(
                    id,
                    userDTO.getFirstName(),
                    userDTO.getLastName(),
                    userDTO.getEmail(),
                    userDTO.getPassword(),
                    userDTO.isEnabled(),
                    userDTO.isEmailVerified(),
                    userDTO.getAttributes(),
                    userDTO.getRealmRoles()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("message", "Utilisateur mis à jour avec succès");
            response.put("username", updatedUser.getUsername());
            response.put("enabled", updatedUser.enabled);
            response.put("emailVerified", updatedUser.emailVerified);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }

    // ✅ NOUVEAU ENDPOINT SPÉCIFIQUE POUR LA MISE À JOUR DU PROFIL PERSONNEL
    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateMyProfile(@PathVariable("id") String id, @RequestBody ProfileUpdateDTO profileDTO) {
        try {
            log.info("📝 Mise à jour du profil personnel pour l'utilisateur: {}", id);

            // ✅ 1. Récupérer l'utilisateur existant pour conserver enabled/emailVerified
            UserRepresentation existingUser = keycloakClient.getUserById(id);

            // ✅ 2. Journaliser les valeurs existantes
            log.info("🔵 Valeurs existantes - enabled: {}, emailVerified: {}",
                    existingUser.isEnabled(), existingUser.isEmailVerified());

            // ✅ 3. Mise à jour SEULEMENT des champs autorisés
            UserDto updatedUser = keycloakClient.updateUserProfile(
                    id,
                    profileDTO.getFirstName(),
                    profileDTO.getLastName(),
                    profileDTO.getEmail(),
                    profileDTO.getPhone(),
                    profileDTO.getLocation()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("message", "Profil mis à jour avec succès");
            response.put("firstName", updatedUser.firstName);
            response.put("lastName", updatedUser.lastName);
            response.put("email", updatedUser.email);


            // ✅ Confirmer que enabled/emailVerified n'ont pas changé
            response.put("enabled", updatedUser.enabled);
            response.put("emailVerified", updatedUser.emailVerified);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Erreur mise à jour profil: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Erreur interne: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        try {
            keycloakClient.deleteUser(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Utilisateur supprimé avec succès");
            response.put("id", id);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }
    public static class UserDto {
        public String id;
        public String firstName;
        public String lastName;
        public String username;
        public String email;
        public String departmentId;
        public String departmentName;
        public boolean enabled;
        public boolean emailVerified;  // ✅ AJOUTÉ
        public List<String> roles;

        // ✅ NOUVEAU CONSTRUCTEUR COMPLET (10 paramètres)
        public UserDto(String id,
                       String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName,
                       boolean enabled,
                       boolean emailVerified,
                       List<String> roles) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.username = username;
            this.email = email;
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.enabled = enabled;
            this.emailVerified = emailVerified;
            this.roles = roles;
        }

        // ✅ CONSTRUCTEUR EXISTANT (9 paramètres - sans emailVerified)
        public UserDto(String id,
                       String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName,
                       boolean enabled,
                       List<String> roles) {
            this(id, firstName, lastName, username, email, departmentId, departmentName, enabled, false, roles);
        }

        // ✅ CONSTRUCTEUR EXISTANT (8 paramètres)
        public UserDto(String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName,
                       boolean enabled,
                       List<String> roles) {
            this(null, firstName, lastName, username, email, departmentId, departmentName, enabled, false, roles);
        }

        // ✅ CONSTRUCTEUR EXISTANT (7 paramètres)
        public UserDto(String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName,
                       boolean enabled) {
            this(null, firstName, lastName, username, email, departmentId, departmentName, enabled, false, List.of());
        }

        // ✅ CONSTRUCTEUR EXISTANT (6 paramètres)
        public UserDto(String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName) {
            this(null, firstName, lastName, username, email, departmentId, departmentName, true, false, List.of());
        }



        // ✅ GETTER pour le username
        public String getUsername() {
            return username;
        }
    }


    // ✅ ENDPOINT POUR CHANGER LE MOT DE PASSE AVEC VÉRIFICATION
    @PutMapping("/{id}/password")
    public ResponseEntity<?> changePassword(@PathVariable("id") String id, @RequestBody PasswordChangeDTO passwordDTO) {
        try {
            log.info("🔐 Demande de changement de mot de passe pour l'utilisateur: {}", id);

            // 1. Vérifier que les mots de passe correspondent
            if (!passwordDTO.getNewPassword().equals(passwordDTO.getConfirmPassword())) {
                log.warn("🔴 Les nouveaux mots de passe ne correspondent pas");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Les nouveaux mots de passe ne correspondent pas"
                ));
            }

            // 2. Vérifier que le nouveau mot de passe est valide
            if (passwordDTO.getNewPassword() == null || passwordDTO.getNewPassword().length() < 6) {
                log.warn("🔴 Nouveau mot de passe trop court");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Le nouveau mot de passe doit contenir au moins 6 caractères"
                ));
            }

            // 3. Vérifier que l'ancien mot de passe est correct
            boolean isCurrentPasswordValid = keycloakClient.verifyUserPassword(
                    id,
                    passwordDTO.getCurrentPassword()
            );

            if (!isCurrentPasswordValid) {
                log.warn("🔴 Ancien mot de passe incorrect pour l'utilisateur: {}", id);
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Le mot de passe actuel est incorrect"
                ));
            }

            // 4. Changer le mot de passe dans Keycloak
            keycloakClient.changeUserPassword(id, passwordDTO.getNewPassword());

            log.info("✅ Mot de passe changé avec succès pour l'utilisateur: {}", id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Mot de passe modifié avec succès");
            response.put("id", id);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Erreur changement mot de passe: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erreur interne: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/password-status")
    public ResponseEntity<Map<String, Boolean>> getPasswordStatus(@PathVariable String id) {
        try {
            UserRepresentation user = keycloakClient.getUserById(id);

            // Récupérer les credentials de l'utilisateur
            List<CredentialRepresentation> credentials = keycloakClient.getUserCredentials(id);

            boolean isTemporary = false;
            if (credentials != null && !credentials.isEmpty()) {
                // Vérifier si le mot de passe est temporaire
                isTemporary = credentials.stream()
                        .anyMatch(cred -> cred.isTemporary() != null && cred.isTemporary());
            }

            return ResponseEntity.ok(Map.of("temporary", isTemporary));
        } catch (Exception e) {
            log.error("❌ Erreur vérification statut mot de passe: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    // Récupérer le département d'un employé
    @GetMapping("/{userId}/department")
    public ResponseEntity<Long> getUserDepartment(@PathVariable String userId) {
        UserDto user = userService.getUserById(userId);
        if (user == null || user.departmentId == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Long.parseLong(user.departmentId));
    }

    // Récupérer tous les managers
    @GetMapping("/managers")
    public ResponseEntity<List<UserDto>> getAllManagers() {
        List<UserDto> managers = userService.getAllManagers();
        return ResponseEntity.ok(managers);
    }

    // Récupérer le nom d'un utilisateur
    @GetMapping("/{userId}/name")
    public ResponseEntity<String> getUserName(@PathVariable String userId) {
        UserDto user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user.firstName + " " + user.lastName);
    }
    // Ajoutez cette méthode dans UserController.java

    /**
     * Récupère tous les utilisateurs avec le rôle ADMIN
     */
    @GetMapping("/admins")
    public ResponseEntity<List<UserDto>> getAllAdmins() {
        try {
            log.info("👑 Récupération de tous les admins");

            // Récupérer tous les utilisateurs
            List<UserRepresentation> users = keycloakClient.getAllUsers();

            // Filtrer ceux qui ont le rôle ADMIN
            List<UserDto> admins = users.stream()
                    .filter(user -> {
                        try {
                            List<String> roles = keycloakClient.getUserRoles(user.getId());
                            boolean isAdmin = roles.contains("ADMIN");
                            if (isAdmin) {
                                log.info("✅ Admin trouvé: {} ({})", user.getUsername(), user.getId());
                            }
                            return isAdmin;
                        } catch (Exception e) {
                            log.error("❌ Erreur récupération rôles pour user {}: {}", user.getId(), e.getMessage());
                            return false;
                        }
                    })
                    .map(user -> {
                        String departmentId = extractAttribute(user, "departmentId");
                        String departmentName = null;

                        if (departmentId != null) {
                            try {
                                departmentName = departmentService.getDepartmentName(Long.parseLong(departmentId));
                            } catch (Exception e) {
                                // Ignorer si département non trouvé
                            }
                        }

                        List<String> roles = extractRoles(user);

                        return new UserDto(
                                user.getId(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getUsername(),
                                user.getEmail(),
                                departmentId,
                                departmentName,
                                user.isEnabled(),
                                user.isEmailVerified(),
                                roles
                        );
                    })
                    .collect(Collectors.toList());

            log.info("👑 Total admins trouvés: {}", admins.size());
            return ResponseEntity.ok(admins);

        } catch (Exception e) {
            log.error("❌ Erreur récupération des admins: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/{userId}/preferred-currency")
    public ResponseEntity<String> getUserPreferredCurrency(@PathVariable String userId) {
        String currency = userService.getUserPreferredCurrency(userId);
        return ResponseEntity.ok(currency);
    }
    @PutMapping("/{userId}/preferred-currency")
    public ResponseEntity<?> updatePreferredCurrency(@PathVariable String userId,
                                                     @RequestParam String currency) {
        try {
            userService.updateUserPreferredCurrency(userId, currency);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Preferred currency updated successfully");
            response.put("currency", currency.toUpperCase());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating preferred currency for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}