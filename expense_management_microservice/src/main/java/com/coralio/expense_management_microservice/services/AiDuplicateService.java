package com.coralio.expense_management_microservice.services;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
@Service
public class AiDuplicateService {

    @Value("${ai.service.url:http://localhost:8888}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkDuplicate(MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/api/ai/check-duplicate",
                    request, Map.class
            );

            return response.getBody() != null ? response.getBody()
                    : Map.of("isDuplicate", false);

        } catch (Exception e) {
            System.err.println("⚠️ AI Service indisponible: " + e.getMessage());
            return Map.of("isDuplicate", false, "reason", "Service IA indisponible");
        }
    }
}