package com.coralio.ai_microservice.controllers;

import com.coralio.ai_microservice.model.DuplicateResult;
import com.coralio.ai_microservice.services.DuplicateDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private DuplicateDetectionService service;

    @PostMapping("/check-duplicate")
    public DuplicateResult check(
            @RequestParam MultipartFile file,
            @RequestParam(value = "employeeId", required = false) String employeeId
    ) throws Exception {
        return service.checkDuplicate(file, employeeId);
    }

    @PostMapping("/validate-receipt")
    public ResponseEntity<Map<String, Object>> validateReceipt(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) Double amount,
            @RequestParam(required = false) String expenseDate,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Map<String, Object> additionalFields
    ) throws Exception {
        Map<String, Object> result = service.validateReceipt(file, amount, expenseDate, description, categoryId, additionalFields);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/extract-fields")
    public ResponseEntity<Map<String, Object>> extractFields(
            @RequestParam MultipartFile file,
            @RequestParam(value = "fields", required = false) String fieldsJson
    ) throws Exception {
        return ResponseEntity.ok(service.extractFields(file, fieldsJson));
    }

    // ========== NOUVEAUX ENDPOINTS ==========

    @PostMapping("/validate-from-json")
    public ResponseEntity<Map<String, Object>> validateFromJson(@RequestBody Map<String, Object> extractedJson) throws Exception {
        Map<String, Object> result = service.validateFromJson(extractedJson);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/check-duplicate-from-text")
    public DuplicateResult checkDuplicateFromText(
            @RequestBody Map<String, String> payload,
            @RequestParam(value = "employeeId", required = false) String employeeId
    ) throws Exception {
        String ocrText = payload.get("ocrText");
        String filepath = payload.get("filepath");
        String excludePath = payload.get("excludePath");   // ← ADD THIS
        return service.checkDuplicateFromText(ocrText, employeeId, filepath, excludePath);
    }
}