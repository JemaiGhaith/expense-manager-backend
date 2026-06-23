package com.coralio.chatbotmicroservice.controller;


import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.service.SmartChatbotService;
import com.coralio.chatbotmicroservice.repository.CategoryRepository;
import com.coralio.chatbotmicroservice.repository.ExpenseNoteRepository;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/smart")
public class SmartChatbotController {

    @Autowired
    private SmartChatbotService smartChatbotService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseNoteRepository expenseNoteRepository;

    @Value("${chatbot.smart.enabled:true}")
    private boolean smartEnabled;

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askSmart(@Valid @RequestBody ChatRequest request) {
        log.info("📱 Smart request from user {}: {}", request.getUserId(), request.getQuestion());

        if (!smartEnabled) {
            return ResponseEntity.ok(ChatResponse.builder()
                    .answer("Le chatbot intelligent est actuellement désactivé. Veuillez utiliser l'API standard.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("disabled")
                    .build());
        }

        ChatResponse response = smartChatbotService.processSmartQuestion(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "ok");
        status.put("service", "smart-chatbot");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("smartEnabled", smartEnabled);
        status.put("model", "llama3.1:8b-instruct-q4_0");

        try {
            status.put("database", "connected");
            status.put("categories", categoryRepository.count());
            status.put("activeCategories", categoryRepository.countActive());
        } catch (Exception e) {
            status.put("database", "error: " + e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("smartEnabled", smartEnabled);
        stats.put("totalCategories", categoryRepository.count());
        stats.put("activeCategories", categoryRepository.countActive());
        stats.put("averagePlafond", categoryRepository.getAveragePlafond());
        stats.put("maxPlafond", categoryRepository.getMaxPlafond());
        stats.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/feedback")
    public ResponseEntity<String> submitFeedback(
            @RequestParam String sessionId,
            @RequestParam int rating,
            @RequestParam(required = false) String comment) {

        log.info("Feedback received - Session: {}, Rating: {}, Comment: {}",
                sessionId, rating, comment);

        // Store feedback in database or log file
        return ResponseEntity.ok("Merci pour votre retour !");
    }
}