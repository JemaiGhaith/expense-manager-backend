package com.coralio.ai_microservice.controllers;


import com.coralio.ai_microservice.model.DuplicateResult;
import com.coralio.ai_microservice.services.DuplicateDetectionService;
import com.coralio.ai_microservice.services.PythonAnalyzeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    DuplicateDetectionService service;
    @Autowired
    private PythonAnalyzeService pythonAnalyzeService;

    @PostMapping("/check-duplicate")
    public DuplicateResult check(
            @RequestParam MultipartFile file,
            @RequestParam(value = "employeeId", required = false) String employeeId
    ) throws Exception {
        return service.checkDuplicate(file, employeeId);  // ✅ passer employeeId
    }


    // ✅ Ajouter cette méthode dans la classe AiController
    @PostMapping("/extract-fields")
    public ResponseEntity<Map<String, Object>> extractFields(
            @RequestParam MultipartFile file,
            @RequestParam(value = "fields", required = false) String fieldsJson
    ) throws Exception {
        return ResponseEntity.ok(service.extractFields(file, fieldsJson));
    }

    @PostMapping("/analyze-accord")
    public ResponseEntity<?> analyzeAccord(@RequestParam MultipartFile file) {
        try {
            Object result = pythonAnalyzeService.callPythonAnalyze(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/compare-accord-form")
    public ResponseEntity<?> compareAccordForm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("formData") String formDataJson
    ) {
        try {
            // Appel au service Python
            String pythonUrl = "http://localhost:8000/compare-accord-form";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("form_data", formDataJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.postForObject(pythonUrl, requestEntity, String.class);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}