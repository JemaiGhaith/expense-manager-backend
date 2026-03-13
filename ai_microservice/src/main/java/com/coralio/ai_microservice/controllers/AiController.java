package com.coralio.ai_microservice.controllers;


import com.coralio.ai_microservice.model.DuplicateResult;
import com.coralio.ai_microservice.services.DuplicateDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    DuplicateDetectionService service;

    @PostMapping("/check-duplicate")
    public DuplicateResult check(@RequestParam MultipartFile file) throws Exception{

        return service.checkDuplicate(file);
    }
}