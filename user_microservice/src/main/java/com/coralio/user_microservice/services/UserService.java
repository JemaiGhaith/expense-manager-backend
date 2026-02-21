package com.coralio.user_microservice.services;

import com.coralio.user_microservice.controllers.UserController.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final KeycloakAdminClient keycloakClient;
    private final DepartmentService departmentService;

    /**
     * Récupère un utilisateur par son ID
     */
    public UserDto getUserById(String userId) {
        try {
            log.info("🔍 Récupération utilisateur: {}", userId);

            UserRepresentation user = keycloakClient.getUserById(userId);
            if (user == null) {
                log.warn("⚠️ Utilisateur non trouvé: {}", userId);
                return null;
            }

            String departmentId = extractAttribute(user, "departmentId");
            String departmentName = null;

            if (departmentId != null) {
                try {
                    departmentName = departmentService.getDepartmentName(Long.parseLong(departmentId));
                } catch (Exception e) {
                    log.warn("Département non trouvé pour ID: {}", departmentId);
                }
            }

            List<String> roles = keycloakClient.getUserRoles(userId);

            return new UserDto(
                    user.getId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getUsername(),
                    user.getEmail(),
                    departmentId,
                    departmentName,
                    user.isEnabled(),
                    user.isEmailVerified(),
                    roles
            );
        } catch (Exception e) {
            log.error("❌ Erreur récupération utilisateur {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Récupère tous les utilisateurs d'un département
     */
    public List<UserDto> getUsersByDepartment(String departmentId) {
        log.info("🔍 Recherche des utilisateurs du département: {}", departmentId);

        List<UserRepresentation> allUsers = keycloakClient.getAllUsers();

        return allUsers.stream()
                .map(user -> {
                    String userDeptId = extractAttribute(user, "departmentId");
                    if (departmentId.equals(userDeptId)) {
                        List<String> roles = keycloakClient.getUserRoles(user.getId());
                        String deptName = null;
                        try {
                            deptName = departmentService.getDepartmentName(Long.parseLong(departmentId));
                        } catch (Exception e) {
                            log.warn("Département non trouvé pour ID: {}", departmentId);
                        }

                        return new UserDto(
                                user.getId(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getUsername(),
                                user.getEmail(),
                                userDeptId,
                                deptName,
                                user.isEnabled(),
                                user.isEmailVerified(),
                                roles
                        );
                    }
                    return null;
                })
                .filter(user -> user != null)
                .collect(Collectors.toList());
    }

    /**
     * Récupère tous les managers (utilisateurs avec rôle MANAGER)
     */
    public List<UserDto> getAllManagers() {
        log.info("🔍 Recherche de tous les managers");

        List<UserRepresentation> allUsers = keycloakClient.getAllUsers();

        return allUsers.stream()
                .map(user -> {
                    List<String> roles = keycloakClient.getUserRoles(user.getId());
                    if (roles.contains("MANAGER")) {
                        String departmentId = extractAttribute(user, "departmentId");
                        String departmentName = null;
                        if (departmentId != null) {
                            try {
                                departmentName = departmentService.getDepartmentName(Long.parseLong(departmentId));
                            } catch (Exception e) {
                                log.warn("Département non trouvé pour ID: {}", departmentId);
                            }
                        }

                        return new UserDto(
                                user.getId(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getUsername(),
                                user.getEmail(),
                                departmentId,
                                departmentName,
                                user.isEnabled(),
                                user.isEmailVerified(),
                                roles
                        );
                    }
                    return null;
                })
                .filter(user -> user != null)
                .collect(Collectors.toList());
    }

    /**
     * Vérifie si un utilisateur est manager
     */
    public boolean isManager(String userId) {
        try {
            List<String> roles = keycloakClient.getUserRoles(userId);
            return roles.contains("MANAGER");
        } catch (Exception e) {
            log.error("❌ Erreur vérification rôle pour {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Vérifie si un utilisateur est dans le même département qu'un manager
     */
    public boolean isUserInSameDepartmentAsManager(String userId, String managerId) {
        try {
            UserDto user = getUserById(userId);
            UserDto manager = getUserById(managerId);

            if (user == null || manager == null) {
                return false;
            }

            // ✅ Accès direct aux champs publics
            String userDept = user.departmentId;
            String managerDept = manager.departmentId;

            boolean sameDepartment = userDept != null && userDept.equals(managerDept);

            log.info("🔍 Vérification département: user={}, manager={}, même département: {}",
                    userDept, managerDept, sameDepartment);

            return sameDepartment;

        } catch (Exception e) {
            log.error("❌ Erreur vérification département: {}", e.getMessage());
            return false;
        }
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
}