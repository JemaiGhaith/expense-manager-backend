package com.coralio.user_microservice.controllers;

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

        // ✅ UTILISER keycloakClient.getUserRoles()
        List<String> roles = keycloakClient.getUserRoles(id);

        return ResponseEntity.ok(
                new UserDto(
                        user.getFirstName(),
                        user.getLastName(),
                        user.getUsername(),
                        user.getEmail(),
                        departmentId,
                        departmentName,
                        user.isEnabled(),
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
                            // Ignorer
                        }
                    }

                    // ✅ UTILISER keycloakClient.getUserRoles() - C'EST LA CLÉ !
                    List<String> roles = keycloakClient.getUserRoles(user.getId());

                    return new UserDto(
                            user.getFirstName(),
                            user.getLastName(),
                            user.getUsername(),
                            user.getEmail(),
                            departmentId,
                            departmentName,
                            user.isEnabled(),
                            roles
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
     * DTO complet avec tous les champs nécessaires
     */
    public static class UserDto {
        public String firstName;
        public String lastName;
        public String username;
        public String email;
        public String departmentId;
        public String departmentName;
        public boolean enabled;
        public List<String> roles;

        // Constructeur complet
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
            this.roles = roles != null ? roles : List.of();
        }
    }
}