package com.coralio.ai_microservice.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class OllamaClient {

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.model:llava-llama3}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public String chatWithImages(String systemPrompt,
                                 String userText,
                                 List<String> base64Images) throws Exception {

        System.out.println("🤖 [OLLAMA] Envoi requête vision");
        System.out.println("🤖 [OLLAMA] Modèle: " + model);
        System.out.println("🤖 [OLLAMA] Images: " + base64Images.size());

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);

        ArrayNode messages = mapper.createArrayNode();

        // System message
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysMsg = mapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        // User message avec images
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userText);

        if (!base64Images.isEmpty()) {
            ArrayNode images = mapper.createArrayNode();
            for (String img : base64Images) {
                images.add(img);
            }
            userMsg.set("images", images);
        }

        messages.add(userMsg);
        body.set("messages", messages);

        String requestBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        System.out.println("🤖 [OLLAMA] Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            System.err.println("❌ [OLLAMA] Erreur: " + response.body());
            throw new RuntimeException("Ollama error " + response.statusCode()
                    + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String content = json.path("message").path("content").asText();
        System.out.println("✅ [OLLAMA] Réponse reçue: "
                + content.substring(0, Math.min(200, content.length())));

        return content;
    }
}