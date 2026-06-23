package com.coralio.ai_microservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PythonAnalyzeService {

    @Value("${python.ai.url:http://localhost:9000}")
    private String pythonBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public Object callPythonAnalyze(MultipartFile file) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // ✅ Correction : 2 arguments (byte[], description)
        ByteArrayResource resource = new ByteArrayResource(file.getBytes(), file.getOriginalFilename()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String url = pythonBaseUrl + "/analyze";  // ✅ use injected base URL

        ResponseEntity<Object> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Object.class
        );
        return response.getBody();
    }
}