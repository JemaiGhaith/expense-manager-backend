package com.coralio.user_microservice.services;

import com.coralio.user_microservice.dto.ProfileUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final KeycloakAdminClient keycloakClient;

    // Récupérer les rôles d'un utilisateur
    public List<String> getUserRoles(String userId) {
        try {
            return keycloakClient.getUserRoles(userId);
        } catch (Exception e) {
            log.error("❌ Error getting user roles: {}", e.getMessage());
            return List.of();
        }
    }

    // Mettre à jour le profil d'un utilisateur
    public void updateUserProfile(String userId, ProfileUpdateDTO updateDTO) {
        log.info("📝 Mise à jour du profil pour l'utilisateur: {}", userId);

        UserRepresentation user = keycloakClient.getUserById(userId);
        if (user == null) {
            log.error("❌ Utilisateur non trouvé: {}", userId);
            throw new RuntimeException("User not found");
        }

        boolean updated = false;

        if (updateDTO.getFirstName() != null && !updateDTO.getFirstName().equals(user.getFirstName())) {
            user.setFirstName(updateDTO.getFirstName());
            updated = true;
            log.info("   - Prénom mis à jour: {}", updateDTO.getFirstName());
        }

        if (updateDTO.getLastName() != null && !updateDTO.getLastName().equals(user.getLastName())) {
            user.setLastName(updateDTO.getLastName());
            updated = true;
            log.info("   - Nom mis à jour: {}", updateDTO.getLastName());
        }

        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equals(user.getEmail())) {
            user.setEmail(updateDTO.getEmail());
            updated = true;
            log.info("   - Email mis à jour: {}", updateDTO.getEmail());
        }

        // Mettre à jour les attributs
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
        }

        if (updateDTO.getPhone() != null) {
            attributes.put("phone", List.of(updateDTO.getPhone()));
            updated = true;
            log.info("   - Téléphone mis à jour: {}", updateDTO.getPhone());
        }

        if (updateDTO.getLocation() != null) {
            attributes.put("location", List.of(updateDTO.getLocation()));
            updated = true;
            log.info("   - Localisation mis à jour: {}", updateDTO.getLocation());
        }

        if (updated) {
            user.setAttributes(attributes);
            keycloakClient.updateUser(user);
            log.info("✅ Profil mis à jour avec succès pour {}", userId);
        } else {
            log.info("ℹ️ Aucune modification détectée");
        }
    }

    // Récupérer le nom du département (à implémenter avec appel à l'autre microservice)
    public String getDepartmentName(String departmentId) {
        // TODO: Appeler le microservice des départements
        return "Département " + departmentId;
    }

    // Statistiques admin
    public int getTotalUsers() {
        // TODO: Implémenter
        return 0;
    }

    public int getActiveUsers() {
        // TODO: Implémenter
        return 0;
    }

    public int getTotalDepartments() {
        // TODO: Implémenter
        return 0;
    }

    public int getTotalProjects() {
        // TODO: Implémenter
        return 0;
    }

    // Statistiques manager
    public int getPendingExpensesForManager(String managerId) {
        // TODO: Implémenter
        return 0;
    }

    public int getTeamMembersCount(String managerId) {
        // TODO: Implémenter
        return 0;
    }

    // Statistiques employé
    public int getPendingExpensesForEmployee(String employeeId) {
        // TODO: Implémenter
        return 0;
    }

    public int getTotalExpensesForEmployee(String employeeId) {
        // TODO: Implémenter
        return 0;
    }
}