package com.coralio.user_microservice.controllers;

import com.coralio.user_microservice.dto.ProfileUpdateDTO;
import com.coralio.user_microservice.services.KeycloakAdminClient;
import com.coralio.user_microservice.services.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final KeycloakAdminClient keycloakClient;
    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        try {
            log.info("========== PROFILE CONTROLLER ==========");

            if (jwt == null) {
                log.error("❌ JWT is null - Utilisateur non authentifié");
                return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
            }

            String userId = jwt.getSubject();
            log.info("📌 User ID from token (sub): {}", userId);

            // Afficher tous les claims du token
            log.info("📌 Token claims:");
            jwt.getClaims().forEach((key, value) ->
                    log.info("   - {}: {}", key, value)
            );

            String username = jwt.getClaimAsString("preferred_username");
            log.info("📌 Username from token: {}", username);

            // Vérifier si l'utilisateur existe dans Keycloak
            UserRepresentation user = keycloakClient.getUserById(userId);

            if (user == null) {
                log.warn("⚠️ User not found with ID: {}", userId);

                // Fallback: chercher par username
                if (username != null) {
                    log.info("🔍 Trying to find user by username: {}", username);
                    user = keycloakClient.getUserByUsername(username);
                }
            }

            if (user == null) {
                log.error("❌ User not found in Keycloak");
                return ResponseEntity.status(404).body(Map.of("error", "Utilisateur non trouvé"));
            }

            log.info("✅ User found: {} ({})", user.getUsername(), user.getId());

            Map<String, Object> profile = new HashMap<>();
            profile.put("id", user.getId());
            profile.put("username", user.getUsername());
            profile.put("firstName", user.getFirstName());
            profile.put("lastName", user.getLastName());
            profile.put("email", user.getEmail());
            profile.put("enabled", user.isEnabled());
            profile.put("emailVerified", user.isEmailVerified());

            if (user.getAttributes() != null) {
                log.info("📌 User attributes: {}", user.getAttributes());
                if (user.getAttributes().containsKey("phone")) {
                    profile.put("phone", user.getAttributes().get("phone").get(0));
                }
                if (user.getAttributes().containsKey("location")) {
                    profile.put("location", user.getAttributes().get("location").get(0));
                }
                if (user.getAttributes().containsKey("departmentId")) {
                    profile.put("departmentId", user.getAttributes().get("departmentId").get(0));
                }
            }

            List<String> roles = profileService.getUserRoles(user.getId());
            profile.put("roles", roles);

            log.info("✅ Profile loaded successfully for user: {}", user.getUsername());
            return ResponseEntity.ok(profile);

        } catch (Exception e) {
            log.error("❌ Error loading profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Erreur lors du chargement du profil: " + e.getMessage()));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileUpdateDTO updateDTO) {

        try {
            if (jwt == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
            }

            String userId = jwt.getSubject();
            log.info("📝 Updating profile for user: {}", userId);

            profileService.updateUserProfile(userId, updateDTO);

            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));

        } catch (Exception e) {
            log.error("❌ Error updating profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Erreur lors de la mise à jour: " + e.getMessage()));
        }
    }

    // Endpoints spécifiques si besoin
    @GetMapping("/admin/me")
    public ResponseEntity<?> getAdminProfile(@AuthenticationPrincipal Jwt jwt) {
        return getMyProfile(jwt);
    }

    @GetMapping("/manager/me")
    public ResponseEntity<?> getManagerProfile(@AuthenticationPrincipal Jwt jwt) {
        return getMyProfile(jwt);
    }

    @GetMapping("/employee/me")
    public ResponseEntity<?> getEmployeeProfile(@AuthenticationPrincipal Jwt jwt) {
        return getMyProfile(jwt);
    }
}