package com.coralio.user_microservice.services;

import com.coralio.user_microservice.dto.UserUpdateDTO;
import com.coralio.user_microservice.controllers.UserController.UserDto;  // ✅ IMPORTER UserDto
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
@Slf4j  // ✅ AJOUTER CETTE ANNOTATION
@Service
public class KeycloakAdminClient {

    private final Keycloak keycloak;

    private static final String SERVER_URL = "http://localhost:8090";
    private static final String REALM = "coral-io_realm";
    private static final String CLIENT_ID = "admin-client";
    private static final String CLIENT_SECRET = "sXpiIV8tivS92L9iN5dCzVve1rzEfUFi";

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
        return keycloak
                .realm(REALM)
                .users()
                .get(userId)
                .toRepresentation();
    }

    public List<UserRepresentation> getAllUsers() {
        return keycloak
                .realm(REALM)
                .users()
                .list();
    }

    public List<UserRepresentation> getKeycloakUsers() {
        return keycloak.realm(REALM).users().list();
    }

    // ==================== CRÉATION D'UTILISATEUR ====================

    public String createUser(String username,
                             String email,
                             String firstName,
                             String lastName,
                             String password,
                             boolean enabled,
                             boolean emailVerified,
                             Map<String, List<String>> attributes,
                             List<String> realmRoles) {

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
            credential.setTemporary(false);
            user.setCredentials(List.of(credential));

            Response response = keycloak.realm(REALM)
                    .users()
                    .create(user);

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
            List<RoleRepresentation> roles = keycloak.realm(REALM)
                    .roles()
                    .list();

            List<RoleRepresentation> rolesToAssign = roles.stream()
                    .filter(role -> roleNames.contains(role.getName()))
                    .collect(Collectors.toList());

            if (!rolesToAssign.isEmpty()) {
                keycloak.realm(REALM)
                        .users()
                        .get(userId)
                        .roles()
                        .realmLevel()
                        .add(rolesToAssign);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'assignation des rôles: " + e.getMessage());
        }
    }

    // ==================== MISE À JOUR D'UTILISATEUR ====================

    public UserDto updateUser(String userId,
                              String firstName,
                              String lastName,
                              String email,
                              String password,
                              boolean enabled,
                              boolean emailVerified,
                              Map<String, List<String>> attributes,
                              List<String> realmRoles) {

        try {
            // 1. Récupérer l'utilisateur existant
            UserRepresentation user = keycloak.realm(REALM)
                    .users()
                    .get(userId)
                    .toRepresentation();

            // 2. Mettre à jour les champs
            if (firstName != null) user.setFirstName(firstName);
            if (lastName != null) user.setLastName(lastName);
            if (email != null) user.setEmail(email);

            user.setEnabled(enabled);
            user.setEmailVerified(emailVerified);

            // 3. Mettre à jour les attributs
            if (attributes != null && !attributes.isEmpty()) {
                user.setAttributes(attributes);
            } else {
                // Si attributes est null ou vide, on peut soit laisser les anciens, soit les supprimer
                // Option: supprimer les attributs si on veut les effacer
                // user.setAttributes(null);
            }

            // 4. Sauvegarder les modifications
            keycloak.realm(REALM)
                    .users()
                    .get(userId)
                    .update(user);

            // 5. Mettre à jour le mot de passe si fourni
            if (password != null && !password.trim().isEmpty()) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(password);
                credential.setTemporary(false);

                keycloak.realm(REALM)
                        .users()
                        .get(userId)
                        .resetPassword(credential);
            }

            // 6. Mettre à jour les rôles si nécessaire
            if (realmRoles != null && !realmRoles.isEmpty()) {
                updateUserRoles(userId, realmRoles);
            }

            // 7. Retourner l'utilisateur mis à jour au format UserDto
            return mapToUserDto(user);

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la mise à jour de l'utilisateur: " + e.getMessage(), e);
        }
    }

    private void updateUserRoles(String userId, List<String> newRoleNames) {
        // Récupérer les rôles actuels
        List<RoleRepresentation> currentRoles = keycloak.realm(REALM)
                .users()
                .get(userId)
                .roles()
                .realmLevel()
                .listAll();

        // Supprimer tous les rôles actuels
        if (!currentRoles.isEmpty()) {
            keycloak.realm(REALM)
                    .users()
                    .get(userId)
                    .roles()
                    .realmLevel()
                    .remove(currentRoles);
        }

        // Ajouter les nouveaux rôles
        if (!newRoleNames.isEmpty()) {
            List<RoleRepresentation> roles = keycloak.realm(REALM)
                    .roles()
                    .list();

            List<RoleRepresentation> rolesToAdd = roles.stream()
                    .filter(role -> newRoleNames.contains(role.getName()))
                    .collect(Collectors.toList());

            if (!rolesToAdd.isEmpty()) {
                keycloak.realm(REALM)
                        .users()
                        .get(userId)
                        .roles()
                        .realmLevel()
                        .add(rolesToAdd);
            }
        }
    }
    // ✅ NOUVELLE MÉTHODE - Mise à jour simple d'un utilisateur
    public void updateUser(UserRepresentation user) {
        try {
            keycloak.realm(REALM)
                    .users()
                    .get(user.getId())
                    .update(user);

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
            List<UserRepresentation> users = keycloak.realm(REALM)
                    .users()
                    .search(username, true);

            if (users.isEmpty()) {
                log.warn("⚠️ Aucun utilisateur trouvé avec username: {}", username);
                return null;
            }

            // Prendre le premier résultat (le plus pertinent)
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
            if (values != null && !values.isEmpty()) {
                departmentId = values.get(0);
            }
        }

        // Récupérer les rôles (simplifié)
        List<String> roles = List.of(); // À implémenter si besoin

        return new UserDto(
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getEmail(),
                departmentId,
                null, // departmentName (à remplir si besoin)
                user.isEnabled(),
                roles
        );
    }
    public void deleteUser(String userId) {
        try {
            Response response = keycloak.realm(REALM)
                    .users()
                    .delete(userId);

            if (response.getStatus() != 204) {
                String error = response.readEntity(String.class);
                throw new RuntimeException("Erreur suppression utilisateur: " + response.getStatus() + " - " + error);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la suppression de l'utilisateur: " + e.getMessage(), e);
        }
    }

    // Ajouter cette méthode dans KeycloakAdminClient.java
    public List<String> getUserRoles(String userId) {
        try {
            // Récupérer les rôles du realm
            List<RoleRepresentation> realmRoles = keycloak.realm(REALM)
                    .users()
                    .get(userId)
                    .roles()
                    .realmLevel()
                    .listAll();

            return realmRoles.stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Erreur récupération rôles: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Mise à jour du profil personnel (sans modification des champs sensibles)
     */
    public UserDto updateUserProfile(String userId,
                                     String firstName,
                                     String lastName,
                                     String email,
                                     String phone,
                                     String location) {
        try {
            log.info("📝 Mise à jour profil utilisateur: {}", userId);

            // Récupérer l'utilisateur existant
            UserRepresentation user = getUserById(userId);

            // ✅ Mettre à jour SEULEMENT les champs autorisés
            if (firstName != null && !firstName.trim().isEmpty()) {
                user.setFirstName(firstName);
            }
            if (lastName != null && !lastName.trim().isEmpty()) {
                user.setLastName(lastName);
            }
            if (email != null && !email.trim().isEmpty()) {
                user.setEmail(email);
            }

            // ✅ Gérer les attributs (phone, location)
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }

            // Gérer le téléphone
            if (phone != null && !phone.trim().isEmpty()) {
                attributes.put("phone", List.of(phone));
            } else {
                attributes.remove("phone"); // Supprimer si phone est null ou vide
            }

            // Gérer la localisation
            if (location != null && !location.trim().isEmpty()) {
                attributes.put("location", List.of(location));
            } else {
                attributes.remove("location"); // Supprimer si location est null ou vide
            }

            user.setAttributes(attributes);

            // ✅ NE PAS MODIFIER enabled ET emailVerified
            // On garde les valeurs existantes
            log.info("🔵 Conservation enabled: {}, emailVerified: {}",
                    user.isEnabled(), user.isEmailVerified());

            // ✅ CORRECTION 1: Utiliser 'keycloak' au lieu de 'keycloakInstance'
            keycloak.realm(REALM)
                    .users()
                    .get(userId)
                    .update(user);

            log.info("✅ Profil utilisateur {} mis à jour avec succès", userId);

            // ✅ CORRECTION 2: Retourner un UserDto au lieu d'UserRepresentation
            return mapToUserDto(user);

        } catch (Exception e) {
            log.error("❌ Erreur mise à jour profil: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la mise à jour du profil: " + e.getMessage(), e);
        }
    }

    /**
     * Changer le mot de passe d'un utilisateur
     */

    // ✅ VÉRIFIER LE MOT DE PASSE ACTUEL
    public boolean verifyUserPassword(String userId, String currentPassword) {
        try {
            log.info("🔐 Vérification du mot de passe pour l'utilisateur: {}", userId);

            // Récupérer l'utilisateur pour obtenir son username
            UserRepresentation user = getUserById(userId);
            String username = user.getUsername();

            // Créer une connexion Keycloak avec les credentials de l'utilisateur
            Keycloak userKeycloak = KeycloakBuilder.builder()
                    .serverUrl(SERVER_URL)
                    .realm(REALM)
                    .grantType(OAuth2Constants.PASSWORD)
                    .clientId(CLIENT_ID)
                    .clientSecret(CLIENT_SECRET)
                    .username(username)
                    .password(currentPassword)
                    .build();

            // Tenter d'obtenir un token
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
            log.info("🔐 Changement de mot de passe pour l'utilisateur: {}", userId);

            // Créer les credentials
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            // Réinitialiser le mot de passe
            keycloak.realm(REALM)
                    .users()
                    .get(userId)
                    .resetPassword(credential);

            log.info("✅ Mot de passe changé avec succès pour: {}", userId);

        } catch (Exception e) {
            log.error("❌ Erreur changement mot de passe pour {}: {}", userId, e.getMessage());
            throw new RuntimeException("Erreur lors du changement de mot de passe: " + e.getMessage());
        }
    }
}