package com.coralio.chatbotmicroservice.controller;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        log.info("REST request to process question: {}", request.getQuestion());
        ChatResponse response = chatbotService.processQuestion(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/help")
    public ResponseEntity<ChatResponse> getHelp(
            @RequestParam String userId,
            @RequestParam String userRole) {
        log.info("REST request to get help for user: {}", userId);
        ChatResponse response = chatbotService.getHelp(userId, userRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/help/{context}")
    public ResponseEntity<ChatResponse> getContextualHelp(
            @RequestParam String userId,
            @RequestParam String userRole,
            @PathVariable String context) {
        log.info("REST request to get contextual help for user: {}, context: {}", userId, context);
        ChatResponse response = chatbotService.getContextualHelp(userId, userRole, context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Simple chatbot service is running");
    }
}