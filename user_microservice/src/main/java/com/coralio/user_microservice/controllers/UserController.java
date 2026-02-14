package com.coralio.user_microservice.controllers;
/*
import com.coralio.user_microservice.config.KeycloakUserDto;
import com.coralio.user_microservice.services.KeycloakAdminClient;
import com.coralio.user_microservice.services.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
                // Ignorer
            }
        }

        // ✅ CORRECTION : Passer les 6 paramètres
        return ResponseEntity.ok(
                new UserDto(
                        user.getFirstName(),
                        user.getLastName(),
                        user.getUsername(),
                        user.getEmail(),
                        departmentId,
                        departmentName
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
                            // Ignorer
                        }
                    }

                    // ✅ CORRECTION : Passer les 6 paramètres
                    return new UserDto(
                            user.getFirstName(),
                            user.getLastName(),
                            user.getUsername(),
                            user.getEmail(),
                            departmentId,
                            departmentName
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

    // ✅ DTO avec 6 paramètres
    public static class UserDto {
        public String firstName;
        public String lastName;
        public String username;
        public String email;
        public String departmentId;
        public String departmentName;

        public UserDto(String firstName, String lastName, String username, String email,
                       String departmentId, String departmentName) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.username = username;
            this.email = email;
            this.departmentId = departmentId;
            this.departmentName = departmentName;
        }
    }
}

*/
import com.coralio.user_microservice.config.KeycloakUserDto;
import com.coralio.user_microservice.services.KeycloakAdminClient;
import com.coralio.user_microservice.services.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

        // Extraire les rôles (si disponibles)
        List<String> roles = extractRoles(user);

        return ResponseEntity.ok(
                new UserDto(
                        user.getFirstName(),
                        user.getLastName(),
                        user.getUsername(),
                        user.getEmail(),
                        departmentId,
                        departmentName,
                        user.isEnabled(),  // ✅ Statut enabled
                        roles              // ✅ Rôles (optionnel)
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

                    // Extraire les rôles
                    List<String> roles = extractRoles(user);

                    return new UserDto(
                            user.getFirstName(),
                            user.getLastName(),
                            user.getUsername(),
                            user.getEmail(),
                            departmentId,
                            departmentName,
                            user.isEnabled(),  // ✅ Statut enabled
                            roles              // ✅ Rôles
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(userDtos);
    }

    /**
     * Extrait un attribut personnalisé d'un utilisateur Keycloak
     */
    private String extractAttribute(UserRepresentation user, String attributeName) {
        if (user.getAttributes() != null && user.getAttributes().containsKey(attributeName)) {
            List<String> values = user.getAttributes().get(attributeName);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }

    /**
     * Extrait les rôles d'un utilisateur Keycloak
     * Note: Cette méthode est simplifiée - pour les rôles complets,
     * il faudrait aussi récupérer les rôles du realm et des clients
     */
    private List<String> extractRoles(UserRepresentation user) {
        // Cette méthode nécessite des appels supplémentaires à l'API Keycloak
        // Pour une solution complète, il faudrait:
        // 1. Récupérer les rôles du realm
        // 2. Récupérer les rôles des clients

        // Version simplifiée - retourne une liste vide
        // À implémenter selon vos besoins
        return List.of();
    }

    /**
     * DTO complet avec tous les champs nécessaires
     */
    public static class UserDto {
        public String firstName;
        public String lastName;
        public String username;
        public String email;
        public String departmentId;
        public String departmentName;
        public boolean enabled;        // ✅ Statut du compte
        public List<String> roles;     // ✅ Liste des rôles

        // Constructeur avec 8 paramètres
        public UserDto(String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName,
                       boolean enabled,
                       List<String> roles) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.username = username;
            this.email = email;
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.enabled = enabled;
            this.roles = roles;
        }

        // Constructeur avec 7 paramètres (sans rôles)
        public UserDto(String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName,
                       boolean enabled) {
            this(firstName, lastName, username, email, departmentId, departmentName, enabled, List.of());
        }

        // Constructeur avec 6 paramètres (pour compatibilité)
        public UserDto(String firstName,
                       String lastName,
                       String username,
                       String email,
                       String departmentId,
                       String departmentName) {
            this(firstName, lastName, username, email, departmentId, departmentName, true, List.of());
        }
    }
}