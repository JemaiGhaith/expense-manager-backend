package com.coralio.chatbotmicroservice.controller;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.service.HybridChatbotService;
import com.coralio.chatbotmicroservice.service.SmartChatbotService;
import com.coralio.chatbotmicroservice.service.ChatbotService;
import com.coralio.chatbotmicroservice.service.AccessControlService;
import com.coralio.chatbotmicroservice.repository.CategoryRepository;
import com.coralio.chatbotmicroservice.repository.ExpenseNoteRepository;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class HybridChatbotController {

    @Autowired
    private HybridChatbotService hybridChatbotService;

    @Autowired
    private SmartChatbotService smartChatbotService;

    @Autowired
    private ChatbotService simpleChatbotService;

    @Autowired
    private AccessControlService accessControlService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseNoteRepository expenseNoteRepository;

    @Value("${chatbot.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${chatbot.smart.enabled:true}")
    private boolean smartEnabled;

    @Value("${chatbot.simple.enabled:true}")
    private boolean simpleEnabled;

    /**
     * ENDPOINT PRINCIPAL - Traite toutes les questions via le chatbot hybride
     */
    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        String authToken = extractAuthToken();
        log.info("🤖 [HYBRID] Requête de l'utilisateur {} (rôle: {}): {}",
                request.getUserId(), request.getUserRole(), request.getQuestion());

        if (!hybridEnabled) {
            log.warn("⚠️ Chatbot hybride désactivé, fallback vers smart");
            ChatResponse response = smartChatbotService.processSmartQuestion(request);
            return ResponseEntity.ok(response);
        }

        ChatResponse response = hybridChatbotService.processQuestion(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ENDPOINT SMART UNIQUEMENT (pour tests ou fallback)
     */
    @PostMapping("/smart/ask")
    public ResponseEntity<ChatResponse> askSmart(@Valid @RequestBody ChatRequest request) {
        log.info("🧠 [SMART] Requête de l'utilisateur {}: {}", request.getUserId(), request.getQuestion());

        if (!smartEnabled) {
            return ResponseEntity.ok(ChatResponse.builder()
                    .answer("Le chatbot intelligent est désactivé.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("disabled")
                    .build());
        }

        ChatResponse response = smartChatbotService.processSmartQuestion(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ENDPOINT SIMPLE UNIQUEMENT (pour tests ou fallback)
     */
    @PostMapping("/simple/ask")
    public ResponseEntity<ChatResponse> askSimple(@Valid @RequestBody ChatRequest request) {
        log.info("📋 [SIMPLE] Requête de l'utilisateur {}: {}", request.getUserId(), request.getQuestion());

        if (!simpleEnabled) {
            return ResponseEntity.ok(ChatResponse.builder()
                    .answer("Le chatbot simple est désactivé.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("disabled")
                    .build());
        }

        ChatResponse response = simpleChatbotService.processQuestion(request);
        return ResponseEntity.ok(response);
    }

    /**
     * AIDE - Retourne l'aide selon le rôle
     */
    @GetMapping("/help")
    public ResponseEntity<ChatResponse> getHelp(
            @RequestParam String userId,
            @RequestParam String userRole) {
        log.info("❓ Demande d'aide pour utilisateur {} (rôle: {})", userId, userRole);

        ChatRequest request = ChatRequest.builder()
                .userId(userId)
                .userRole(userRole)
                .question("aide")
                .sessionId("help-" + System.currentTimeMillis())
                .build();

        ChatResponse response = simpleChatbotService.getHelp(userId, userRole);
        return ResponseEntity.ok(response);
    }

    /**
     * AIDE CONTEXTUELLE - Aide spécifique à un contexte
     */
    @GetMapping("/help/{context}")
    public ResponseEntity<ChatResponse> getContextualHelp(
            @RequestParam String userId,
            @RequestParam String userRole,
            @PathVariable String context) {
        log.info("❓ Aide contextuelle pour {} (contexte: {})", userId, context);

        ChatResponse response = simpleChatbotService.getContextualHelp(userId, userRole, context);
        return ResponseEntity.ok(response);
    }

    /**
     * VÉRIFICATION D'ACCÈS - Endpoint pour tester les droits
     */
    @PostMapping("/check-access")
    public ResponseEntity<Map<String, Object>> checkAccess(
            @Valid @RequestBody ChatRequest request) {
        String authToken = extractAuthToken();
        log.info("🔐 Vérification d'accès pour utilisateur {} sur question: {}",
                request.getUserId(), request.getQuestion());

        AccessControlService.AccessResult result = accessControlService.checkNoteAccess(
                request.getQuestion(),
                request.getUserId(),
                request.getUserRole(),
                authToken
        );

        Map<String, Object> response = new HashMap<>();
        response.put("hasAccess", result.hasAccess);
        response.put("noteId", result.noteId);
        response.put("reason", result.reason);
        response.put("code", result.code);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * STATISTIQUES - Infos sur le chatbot
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Configuration
        stats.put("hybridEnabled", hybridEnabled);
        stats.put("smartEnabled", smartEnabled);
        stats.put("simpleEnabled", simpleEnabled);

        // Statistiques DB
        try {
            stats.put("totalCategories", categoryRepository.count());
            stats.put("activeCategories", categoryRepository.countActive());
            stats.put("averagePlafond", categoryRepository.getAveragePlafond());
            stats.put("maxPlafond", categoryRepository.getMaxPlafond());
            stats.put("totalExpenseNotes", expenseNoteRepository.count());
        } catch (Exception e) {
            stats.put("databaseError", e.getMessage());
        }

        stats.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    /**
     * HEALTH CHECK - État du service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "hybrid-chatbot");
        status.put("hybridEnabled", hybridEnabled);
        status.put("smartEnabled", smartEnabled);
        status.put("simpleEnabled", simpleEnabled);
        status.put("timestamp", LocalDateTime.now().toString());

        // Vérification des microservices via gateway
        try {
            // À implémenter : vérifier la connexion aux autres services
            status.put("database", "connected");
        } catch (Exception e) {
            status.put("database", "error: " + e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    /**
     * FEEDBACK - Retour utilisateur sur les réponses
     */
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> submitFeedback(
            @RequestParam String sessionId,
            @RequestParam int rating,
            @RequestParam(required = false) String comment) {

        log.info("📝 Feedback reçu - Session: {}, Note: {}, Commentaire: {}",
                sessionId, rating, comment);

        // Ici vous pouvez stocker le feedback dans une base de données

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Merci pour votre retour !");

        return ResponseEntity.ok(response);
    }

    /**
     * TEST DE CLASSIFICATION - Pour debug (à désactiver en prod)
     */
    @PostMapping("/debug/classify")
    public ResponseEntity<Map<String, Object>> debugClassify(@Valid @RequestBody ChatRequest request) {
        // Endpoint de debug - à désactiver en production
        Map<String, Object> debug = new HashMap<>();
        debug.put("question", request.getQuestion());
        debug.put("userId", request.getUserId());
        debug.put("userRole", request.getUserRole());

        // Simulation de classification
        boolean isComplex = request.getQuestion().length() > 50 ||
                request.getQuestion().contains("pourquoi") ||
                request.getQuestion().contains("analyse");

        debug.put("isComplex", isComplex);
        debug.put("recommendedBot", isComplex ? "smart" : "simple");
        debug.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(debug);
    }

    /**
     * Extrait le token JWT de la requête
     */
    private String extractAuthToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible d'extraire le token: {}", e.getMessage());
        }
        return null;
    }
}