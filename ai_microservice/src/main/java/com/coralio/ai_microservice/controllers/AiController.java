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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private DuplicateDetectionService service;

    @Autowired
    private PythonAnalyzeService pythonAnalyzeService;  // calls port 8000

    @Autowired
    private ObjectMapper objectMapper;

    // ========== YOUR ADVANCED ENDPOINTS (use port 9000) ==========
    @PostMapping("/check-duplicate")
    public DuplicateResult check(@RequestParam MultipartFile file,
                                 @RequestParam(value = "employeeId", required = false) String employeeId) throws Exception {
        return service.checkDuplicate(file, employeeId);
    }

    @PostMapping("/extract-fields")
    public ResponseEntity<Map<String, Object>> extractFields(@RequestParam MultipartFile file,
                                                             @RequestParam(value = "fields", required = false) String fieldsJson) {
        return ResponseEntity.ok(service.extractFields(file, fieldsJson));
    }

    @PostMapping("/validate-receipt")
    public ResponseEntity<Map<String, Object>> validateReceipt(@RequestParam MultipartFile file,
                                                               @RequestParam(required = false) Double amount,
                                                               @RequestParam(required = false) String expenseDate,
                                                               @RequestParam(required = false) String description,
                                                               @RequestParam(required = false) Long categoryId,
                                                               @RequestParam(required = false) Map<String, Object> additionalFields) throws Exception {
        return ResponseEntity.ok(service.validateReceipt(file, amount, expenseDate, description, categoryId, additionalFields));
    }

    @PostMapping("/validate-from-json")
    public ResponseEntity<Map<String, Object>> validateFromJson(@RequestBody Map<String, Object> extractedJson) throws Exception {
        return ResponseEntity.ok(service.validateFromJson(extractedJson));
    }

    @PostMapping("/check-duplicate-from-text")
    public DuplicateResult checkDuplicateFromText(@RequestBody Map<String, String> payload,
                                                  @RequestParam(value = "employeeId", required = false) String employeeId) throws Exception {
        return service.checkDuplicateFromText(payload.get("ocrText"), employeeId, payload.get("filepath"), payload.get("excludePath"));
    }

    // ========== ACCORD ENDPOINTS (use port 8000) ==========
    @PostMapping("/analyze-accord")
    public ResponseEntity<?> analyzeAccord(@RequestParam MultipartFile file) {
        try {
            Object result = pythonAnalyzeService.callPythonAnalyze(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/compare-accord-form")
    public ResponseEntity<?> compareAccordForm(@RequestParam("file") MultipartFile file,
                                               @RequestParam("formData") String formDataJson) {
        try {
            String pythonUrl = "http://localhost:8000/compare-accord-form";
            RestTemplate rest = new RestTemplate();
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            });
            body.add("form_data", formDataJson);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            String response = rest.postForObject(pythonUrl, request, String.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-full")
    public ResponseEntity<?> analyzeFull(@RequestParam("file") MultipartFile file,
                                         @RequestParam("form_data") String formDataJson) {
        try {
            String pythonUrl = "http://localhost:8000/analyze-full";
            RestTemplate rest = new RestTemplate();
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            });
            body.add("form_data", formDataJson);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            String pythonResponse = rest.postForObject(pythonUrl, request, String.class);
            return ResponseEntity.ok(pythonResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}