package com.coralio.user_microservice.services;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeycloakAdminClient {

    private final Keycloak keycloak;

    private static final String SERVER_URL = "http://localhost:8090";
    private static final String REALM = "coral-io_realm";
    private static final String CLIENT_ID = "admin-client";
    private static final String CLIENT_SECRET = "sXpiIV8tivS92L9iN5dCzVve1rzEfUFi";

    // Constructor initialization ensures keycloak is never null
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
                .list(); // Third parameter = briefRepresentation
    }

    public List<UserRepresentation> getKeycloakUsers() {
        // ✅ Simply fetch users from Keycloak
        return keycloak.realm(REALM).users().list();
    }
}
