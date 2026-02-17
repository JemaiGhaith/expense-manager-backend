package com.coralio.user_microservice.controllers;

import com.coralio.user_microservice.dto.UserCreateDTO;
import com.coralio.user_microservice.dto.UserUpdateDTO;
import com.coralio.user_microservice.services.KeycloakAdminClient;
import com.coralio.user_microservice.services.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final KeycloakAdminClient keycloakClient;
    private final DepartmentService departmentService;

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
        // Version simplifiée - retourne une liste vide
        return List.of();
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
}