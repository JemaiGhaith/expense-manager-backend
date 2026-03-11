package com.coralio.ai_microservice.controllers;

import com.coralio.ai_microservice.dto.DuplicateCheckResponse;
import com.coralio.ai_microservice.services.DuplicateDetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
public class DuplicateDetectionController {

    private final DuplicateDetectionService detectionService;

    public DuplicateDetectionController(DuplicateDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping(value = "/check-duplicate", consumes = "multipart/form-data")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "employeeId", required = false) String employeeId
    ) {
        try {
            DuplicateCheckResponse response = detectionService.checkDuplicate(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(DuplicateCheckResponse.builder()
                            .isDuplicate(false)
                            .reason("Erreur serveur: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"AI Service MCP OK\"}");
    }
}