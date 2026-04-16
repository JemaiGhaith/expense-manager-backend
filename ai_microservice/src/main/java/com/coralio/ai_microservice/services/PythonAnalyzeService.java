package com.coralio.ai_microservice.services;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PythonAnalyzeService {

    private static final String PYTHON_ANALYZE_URL = "http://localhost:8000/analyze";
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

        ResponseEntity<Object> response = restTemplate.exchange(
                PYTHON_ANALYZE_URL,
                HttpMethod.POST,
                requestEntity,
                Object.class
        );
        return response.getBody();
    }
}