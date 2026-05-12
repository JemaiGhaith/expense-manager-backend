// expense-microservice/src/main/java/com/coralio/expense_management_microservice/client/UserServiceClient.java
package com.coralio.expense_management_microservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    // ✅ Utilisez le GATEWAY au lieu du user-service direct
    @Value("${gateway.url:http://localhost:8888}")
    private String gatewayUrl;

    // ✅ Alternative : garder les deux URLs avec fallback
    @Value("${user.service.url:http://localhost:8083}")
    private String userServiceUrl;

    public String getUserEmail(String userId) {
        if (userId == null) return null;

        // ✅ Méthode 1: Essayer d'abord via le GATEWAY
        String email = getUserEmailViaGateway(userId);
        if (email != null && isValidEmail(email)) {
            log.info("✅ Email trouvé via GATEWAY pour {}: {}", userId, email);
            return email;
        }

        // ✅ Méthode 2: Fallback direct vers user-service
        email = getUserEmailViaDirect(userId);
        if (email != null && isValidEmail(email)) {
            log.info("✅ Email trouvé via DIRECT pour {}: {}", userId, email);
            return email;
        }

        log.warn("⚠️ Aucun email trouvé pour l'utilisateur: {}", userId);
        return null;  // ❌ Ne jamais retourner userId + "@coralio.com"
    }

    private String getUserEmailViaGateway(String userId) {
        try {
            // Appel via le gateway
            String url = gatewayUrl + "/api/users/" + userId;
            log.debug("📧 Fetching user via gateway: {}", url);

            Map<String, Object> user = restTemplate.getForObject(url, Map.class);
            if (user != null && user.containsKey("email")) {
                String email = (String) user.get("email");
                if (isValidEmail(email)) {
                    return email;
                }
            }
        } catch (Exception e) {
            log.warn("❌ Erreur appel gateway pour {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private String getUserEmailViaDirect(String userId) {
        try {
            // Fallback direct vers user-service
            String url = userServiceUrl + "/api/users/" + userId;
            log.debug("📧 Fetching user via direct: {}", url);

            Map<String, Object> user = restTemplate.getForObject(url, Map.class);
            if (user != null && user.containsKey("email")) {
                String email = (String) user.get("email");
                if (isValidEmail(email)) {
                    return email;
                }
            }
        } catch (Exception e) {
            log.warn("❌ Erreur appel direct pour {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        // ✅ Vérifier que c'est un vrai email, pas un UUID avec @coralio.com
        if (email.contains("-") && email.length() > 30) return false;
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public String getUserName(String userId) {
        if (userId == null) return "Utilisateur";

        // Essayer via gateway
        try {
            String url = gatewayUrl + "/api/users/" + userId;
            Map<String, Object> user = restTemplate.getForObject(url, Map.class);

            if (user != null) {
                String firstName = (String) user.get("firstName");
                String lastName = (String) user.get("lastName");
                if (firstName != null && lastName != null) {
                    return firstName + " " + lastName;
                }
            }
        } catch (Exception e) {
            log.warn("Erreur récupération nom via gateway: {}", e.getMessage());
        }

        // Fallback direct
        try {
            String url = userServiceUrl + "/api/users/" + userId;
            Map<String, Object> user = restTemplate.getForObject(url, Map.class);

            if (user != null) {
                String firstName = (String) user.get("firstName");
                String lastName = (String) user.get("lastName");
                if (firstName != null && lastName != null) {
                    return firstName + " " + lastName;
                }
            }
        } catch (Exception e) {
            log.warn("Erreur récupération nom direct: {}", e.getMessage());
        }

        return "Utilisateur";
    }

    // Pour récupérer l'utilisateur complet
    public Map<String, Object> getUser(String userId) {
        try {
            String url = gatewayUrl + "/api/users/" + userId;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("❌ Erreur récupération user: {}", e.getMessage());
            return null;
        }
    }
    public String getUserPreferredCurrency(String userId) {
        try {
            String url = "http://localhost:8083/api/users/" + userId + "/preferred-currency";
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.warn("Cannot fetch preferred currency for user {}, falling back to TND", userId);
            return "TND";
        }
    }
}