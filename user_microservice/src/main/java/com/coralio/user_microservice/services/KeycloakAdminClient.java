package com.coralio.user_microservice.services;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    // ✅ VERSION CORRIGÉE: Récupérer les rôles d'un utilisateur
    public List<String> getUserRoles(String userId) {
        List<String> roles = new ArrayList<>();

        try {
            UserRepresentation user = getUserById(userId);
            System.out.println("🔍 Récupération des rôles pour: " + user.getUsername());

            // Récupérer TOUS les rôles disponibles pour l'utilisateur
            List<RoleRepresentation> allRoles = keycloak
                    .realm(REALM)
                    .users()
                    .get(userId)
                    .roles()
                    .realmLevel()
                    .listAll();

            System.out.println("📋 Tous les rôles (bruts): " +
                    allRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toList()));

            // Filtrer pour ne garder que ADMIN, MANAGER, EMPLOYEE
            for (RoleRepresentation role : allRoles) {
                String roleName = role.getName();
                if (roleName.equals("ADMIN") ||
                        roleName.equals("MANAGER") ||
                        roleName.equals("EMPLOYEE")) {
                    roles.add(roleName);
                }
            }

            // Si aucun rôle trouvé, utiliser le fallback
            if (roles.isEmpty()) {
                String username = user.getUsername().toLowerCase();
                if (username.contains("admin") || "test_admin".equals(username)) {
                    roles.add("ADMIN");  // ✅ Seulement ADMIN, pas MANAGER
                } else if (username.contains("manager")) {
                    roles.add("MANAGER");
                } else {
                    roles.add("EMPLOYEE");
                }
            }

            // ✅ SUPPRIMER cette ligne qui ajoute MANAGER à ADMIN
            // if (roles.contains("ADMIN") && !roles.contains("MANAGER")) {
            //     roles.add("MANAGER");
            // }

            System.out.println("✅ Rôles finaux: " + roles);

        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();

            // Fallback
            try {
                UserRepresentation user = getUserById(userId);
                String username = user.getUsername().toLowerCase();
                if (username.contains("admin") || "test_admin".equals(username)) {
                    roles.add("ADMIN");  // ✅ Seulement ADMIN
                } else if (username.contains("manager")) {
                    roles.add("MANAGER");
                } else {
                    roles.add("EMPLOYEE");
                }
            } catch (Exception ex) {
                roles.add("EMPLOYEE");
            }
        }

        return roles.stream().distinct().collect(Collectors.toList());
    }
    // Méthode utilitaire pour obtenir l'ID d'un client par son nom
    private String getClientId(String clientName) {
        try {
            return keycloak
                    .realm(REALM)
                    .clients()
                    .findByClientId(clientName)
                    .get(0)
                    .getId();
        } catch (Exception e) {
            System.err.println("⚠️ Client non trouvé: " + clientName);
            return null;
        }
    }

    public List<UserRepresentation> getKeycloakUsers() {
        return keycloak.realm(REALM).users().list();
    }
}