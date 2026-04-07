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

    @Value("${user.service.url:http://localhost:8888}")
    private String userServiceUrl;

    public String getUserEmail(String userId) {
        try {
            // First try to get from /email endpoint
            String url = userServiceUrl + "/api/users/" + userId + "/email";
            log.info("📧 Fetching email for user: {}", userId);
            String email = restTemplate.getForObject(url, String.class);
            if (email != null && !email.isEmpty()) {
                log.info("✅ Found email: {}", email);
                return email;
            }
        } catch (Exception e) {
            log.warn("Could not get email from /email endpoint, trying full user endpoint");
        }

        // Fallback: Get full user object
        try {
            String url = userServiceUrl + "/api/users/" + userId;
            Map<String, Object> user = restTemplate.getForObject(url, Map.class);
            if (user != null && user.containsKey("email")) {
                String email = (String) user.get("email");
                log.info("✅ Found email from user object: {}", email);
                return email;
            }
        } catch (Exception e) {
            log.warn("Could not get user from user service");
        }

        // Last resort fallback
        log.warn("⚠️ Using fallback email for user: {}", userId);
        return userId + "@coralio.com";
    }
    // Dans UserServiceClient.java, ajoutez cette méthode:

    public String getUserName(String userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId + "/name";
            log.info("👤 Fetching name for user: {}", userId);
            String name = restTemplate.getForObject(url, String.class);
            if (name != null && !name.isEmpty()) {
                log.info("✅ Found name: {}", name);
                return name;
            }
        } catch (Exception e) {
            log.warn("Could not get name for user: {}", userId);
        }
        return "Utilisateur";
    }
}