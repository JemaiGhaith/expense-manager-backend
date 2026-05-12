package com.coralio.user_microservice.services;

import com.coralio.user_microservice.dto.UserUpdateDTO;
import com.coralio.user_microservice.controllers.UserController.UserDto;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeycloakAdminClient {

    private final Keycloak keycloak;

    private static final String SERVER_URL = "http://localhost:8090";
    private static final String REALM = "coral-io_realm";
    private static final String CLIENT_ID = "admin-client";
    private static final String CLIENT_SECRET = "vov2JMbDjbDsZQCyJeBZIqcH2W5blsdD";

    public KeycloakAdminClient() {
        keycloak = KeycloakBuilder.builder()
                .serverUrl(SERVER_URL)
                .realm(REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .build();
    }

    public UserRepresentation getUserById(String userId) {
        return keycloak.realm(REALM).users().get(userId).toRepresentation();
    }

    public List<UserRepresentation> getAllUsers() {
        return keycloak.realm(REALM).users().list();
    }

    public List<UserRepresentation> getKeycloakUsers() {
        return keycloak.realm(REALM).users().list();
    }

    // ==================== CRÉATION D'UTILISATEUR ====================

    public String createUser(String username, String email, String firstName, String lastName,
                             String password, boolean enabled, boolean emailVerified,
                             Map<String, List<String>> attributes, List<String> realmRoles) {
        try {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(enabled);
            user.setEmailVerified(emailVerified);

            if (attributes != null && !attributes.isEmpty()) {
                user.setAttributes(attributes);
            }

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(true);
            user.setCredentials(List.of(credential));

            Response response = keycloak.realm(REALM).users().create(user);

            if (response.getStatus() == 201) {
                String location = response.getHeaderString("Location");
                String userId = location.substring(location.lastIndexOf("/") + 1);

                if (realmRoles != null && !realmRoles.isEmpty()) {
                    assignRoles(userId, realmRoles);
                }
                return userId;
            } else {
                String error = response.readEntity(String.class);
                throw new RuntimeException("Erreur création utilisateur: " + response.getStatus() + " - " + error);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la création de l'utilisateur: " + e.getMessage(), e);
        }
    }

    private void assignRoles(String userId, List<String> roleNames) {
        try {
            List<RoleRepresentation> roles = keycloak.realm(REALM).roles().list();
            List<RoleRepresentation> rolesToAssign = roles.stream()
                    .filter(role -> roleNames.contains(role.getName()))
                    .collect(Collectors.toList());
            if (!rolesToAssign.isEmpty()) {
                keycloak.realm(REALM).users().get(userId).roles().realmLevel().add(rolesToAssign);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'assignation des rôles: " + e.getMessage());
        }
    }

    // ==================== MISE À JOUR D'UTILISATEUR ====================

    // ✅ Overloaded method (with all fields) – now correctly implemented
    public UserDto updateUser(String userId, String firstName, String lastName, String email, String password,
                              Boolean enabled, Boolean emailVerified, Map<String, List<String>> attributes,
                              List<String> realmRoles) {
        try {
            UserRepresentation user = getUserById(userId);
            if (user == null) throw new RuntimeException("User not found: " + userId);

            if (firstName != null) user.setFirstName(firstName);
            if (lastName != null) user.setLastName(lastName);
            if (email != null) user.setEmail(email);
            if (enabled != null) user.setEnabled(enabled);
            if (emailVerified != null) user.setEmailVerified(emailVerified);
            if (attributes != null && !attributes.isEmpty()) user.setAttributes(attributes);

            // Save user
            keycloak.realm(REALM).users().get(userId).update(user);

            // Update password if provided
            if (password != null && !password.trim().isEmpty()) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(password);
                credential.setTemporary(false);
                keycloak.realm(REALM).users().get(userId).resetPassword(credential);
            }

            // Update roles if provided
            if (realmRoles != null && !realmRoles.isEmpty()) {
                updateUserRoles(userId, realmRoles);
            }

            return mapToUserDto(user);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la mise à jour de l'utilisateur: " + e.getMessage(), e);
        }
    }

    private void updateUserRoles(String userId, List<String> newRoleNames) {
        // Remove current realm roles
        List<RoleRepresentation> currentRoles = keycloak.realm(REALM)
                .users().get(userId).roles().realmLevel().listAll();
        if (!currentRoles.isEmpty()) {
            keycloak.realm(REALM).users().get(userId).roles().realmLevel().remove(currentRoles);
        }
        // Add new roles
        if (!newRoleNames.isEmpty()) {
            List<RoleRepresentation> roles = keycloak.realm(REALM).roles().list();
            List<RoleRepresentation> rolesToAdd = roles.stream()
                    .filter(role -> newRoleNames.contains(role.getName()))
                    .collect(Collectors.toList());
            if (!rolesToAdd.isEmpty()) {
                keycloak.realm(REALM).users().get(userId).roles().realmLevel().add(rolesToAdd);
            }
        }
    }

    // ✅ Simple update using UserRepresentation
    public void updateUser(UserRepresentation user) {
        try {
            keycloak.realm(REALM).users().get(user.getId()).update(user);
            log.info("✅ Utilisateur {} mis à jour", user.getUsername());
        } catch (Exception e) {
            log.error("❌ Erreur mise à jour utilisateur: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la mise à jour", e);
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    public UserRepresentation getUserByUsername(String username) {
        log.info("🔍 Recherche utilisateur par username: {}", username);
        try {
            List<UserRepresentation> users = keycloak.realm(REALM).users().search(username, true);
            if (users.isEmpty()) {
                log.warn("⚠️ Aucun utilisateur trouvé avec username: {}", username);
                return null;
            }
            UserRepresentation user = users.get(0);
            log.info("✅ Utilisateur trouvé: {} (ID: {})", user.getUsername(), user.getId());
            return user;
        } catch (Exception e) {
            log.error("❌ Erreur recherche par username {}: {}", username, e.getMessage());
            return null;
        }
    }

    private UserDto mapToUserDto(UserRepresentation user) {
        String departmentId = null;
        if (user.getAttributes() != null && user.getAttributes().containsKey("departmentId")) {
            List<String> values = user.getAttributes().get("departmentId");
            if (values != null && !values.isEmpty()) departmentId = values.get(0);
        }
        List<String> roles = getUserRoles(user.getId()); // reuse existing method
        return new UserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getEmail(),
                departmentId,
                null, // departmentName – can be resolved later if needed
                user.isEnabled(),
                user.isEmailVerified(),
                roles
        );
    }

    public void deleteUser(String userId) {
        try {
            Response response = keycloak.realm(REALM).users().delete(userId);
            if (response.getStatus() != 204) {
                String error = response.readEntity(String.class);
                throw new RuntimeException("Erreur suppression utilisateur: " + response.getStatus() + " - " + error);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la suppression de l'utilisateur: " + e.getMessage(), e);
        }
    }

    public List<String> getUserRoles(String userId) {
        try {
            List<RoleRepresentation> realmRoles = keycloak.realm(REALM)
                    .users().get(userId).roles().realmLevel().listAll();
            return realmRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Erreur récupération rôles: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== PROFIL PERSONNEL ====================

    public UserDto updateUserProfile(String userId, String firstName, String lastName, String email,
                                     String phone, String location) {
        try {
            log.info("📝 Mise à jour profil utilisateur: {}", userId);
            UserRepresentation user = getUserById(userId);

            if (firstName != null && !firstName.trim().isEmpty()) user.setFirstName(firstName);
            if (lastName != null && !lastName.trim().isEmpty()) user.setLastName(lastName);
            if (email != null && !email.trim().isEmpty()) user.setEmail(email);

            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) attributes = new HashMap<>();

            if (phone != null && !phone.trim().isEmpty()) attributes.put("phone", List.of(phone));
            else attributes.remove("phone");
            if (location != null && !location.trim().isEmpty()) attributes.put("location", List.of(location));
            else attributes.remove("location");

            user.setAttributes(attributes);
            keycloak.realm(REALM).users().get(userId).update(user);
            log.info("✅ Profil utilisateur {} mis à jour", userId);
            return mapToUserDto(user);
        } catch (Exception e) {
            log.error("❌ Erreur mise à jour profil: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la mise à jour du profil: " + e.getMessage(), e);
        }
    }

    // ==================== PASSWORD ====================

    public boolean verifyUserPassword(String userId, String currentPassword) {
        try {
            UserRepresentation user = getUserById(userId);
            String username = user.getUsername();
            Keycloak userKeycloak = KeycloakBuilder.builder()
                    .serverUrl(SERVER_URL)
                    .realm(REALM)
                    .grantType(OAuth2Constants.PASSWORD)
                    .clientId(CLIENT_ID)
                    .clientSecret(CLIENT_SECRET)
                    .username(username)
                    .password(currentPassword)
                    .build();
            userKeycloak.tokenManager().getAccessToken();
            log.info("✅ Mot de passe valide pour l'utilisateur: {}", userId);
            return true;
        } catch (Exception e) {
            log.warn("🔴 Mot de passe invalide pour l'utilisateur: {} - {}", userId, e.getMessage());
            return false;
        }
    }

    public void changeUserPassword(String userId, String newPassword) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);
            keycloak.realm(REALM).users().get(userId).resetPassword(credential);
            log.info("✅ Mot de passe changé avec succès pour: {}", userId);
        } catch (Exception e) {
            log.error("❌ Erreur changement mot de passe pour {}: {}", userId, e.getMessage());
            throw new RuntimeException("Erreur lors du changement de mot de passe: " + e.getMessage());
        }
    }

    public List<CredentialRepresentation> getUserCredentials(String userId) {
        try {
            return keycloak.realm(REALM).users().get(userId).credentials();
        } catch (Exception e) {
            log.error("❌ Erreur récupération credentials: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== CURRENCY ATTRIBUTE ====================

    /**
     * Update a single attribute for a user.
     */
    public void updateUserAttribute(String userId, String key, String value) {
        try {
            UserRepresentation user = getUserById(userId);
            if (user == null) throw new RuntimeException("User not found: " + userId);
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) attributes = new HashMap<>();
            attributes.put(key, List.of(value));
            user.setAttributes(attributes);
            // Use the existing update method that accepts UserRepresentation
            updateUser(user);
        } catch (Exception e) {
            log.error("Failed to update attribute {} for user {}", key, userId, e);
            throw new RuntimeException(e);
        }
    }
}