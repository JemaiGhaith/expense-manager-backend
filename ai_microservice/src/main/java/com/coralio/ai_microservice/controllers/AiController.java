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
    DuplicateDetectionService service;

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
}