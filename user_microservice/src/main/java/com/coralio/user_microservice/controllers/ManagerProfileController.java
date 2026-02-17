/*package com.coralio.user_microservice.controllers;

import com.coralio.user_microservice.dto.ManagerProfileDTO;
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
@RequestMapping("/api/profile/manager")
@RequiredArgsConstructor
@Slf4j
public class ManagerProfileController {

    private final KeycloakAdminClient keycloakClient;
    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        try {
            String userId = jwt.getSubject();
            log.info("📥 Manager profile request for user: {}", userId);

            UserRepresentation user = keycloakClient.getUserById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            ManagerProfileDTO profile = new ManagerProfileDTO();
            profile.setId(user.getId());
            profile.setUsername(user.getUsername());
            profile.setFirstName(user.getFirstName());
            profile.setLastName(user.getLastName());
            profile.setEmail(user.getEmail());
            profile.setEnabled(user.isEnabled());

            // Récupérer les attributs
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes != null) {
                if (attributes.containsKey("phone")) {
                    profile.setPhone(attributes.get("phone").get(0));
                }
                if (attributes.containsKey("location")) {
                    profile.setLocation(attributes.get("location").get(0));
                }
                if (attributes.containsKey("departmentId")) {
                    profile.setDepartmentId(attributes.get("departmentId").get(0));
                }
            }

            // Récupérer le nom du département
            if (profile.getDepartmentId() != null) {
                String deptName = profileService.getDepartmentName(profile.getDepartmentId());
                profile.setDepartmentName(deptName);
            }

            // Récupérer les rôles
            profile.setRoles(profileService.getUserRoles(user.getId()));

            // Statistiques manager
            profile.setPendingExpenses(profileService.getPendingExpensesForManager(userId));
            profile.setTotalTeamMembers(profileService.getTeamMembersCount(userId));

            return ResponseEntity.ok(profile);

        } catch (Exception e) {
            log.error("❌ Error loading manager profile: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileUpdateDTO updateDTO) {

        try {
            String userId = jwt.getSubject();
            log.info("📝 Updating manager profile for user: {}", userId);

            profileService.updateUserProfile(userId, updateDTO);

            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));

        } catch (Exception e) {
            log.error("❌ Error updating profile: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}*/