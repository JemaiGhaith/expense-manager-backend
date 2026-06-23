package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.dto.QuickReply;
import com.coralio.chatbotmicroservice.entity.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class HybridChatbotService {

    @Autowired
    private IntentClassifierService intentClassifier;

    @Autowired
    private AccessControlService accessControl;

    @Autowired
    private ChatbotService simpleChatbotService;

    @Autowired
    private SmartChatbotService smartChatbotService;

    @Autowired
    private RulesDataService rulesDataService;

    @Autowired
    private ResponseFormatter responseFormatter;

    @Autowired
    private ChatSessionService sessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${chatbot.hybrid.simple-confidence-threshold:0.6}")
    private double simpleConfidenceThreshold;

    @Value("${chatbot.hybrid.fallback-to-smart:true}")
    private boolean fallbackToSmart;

    // Social intents that should always use simple chatbot
    private static final Set<String> SOCIAL_INTENTS = Set.of(
            "GREETING", "GRATITUDE", "FAREWELL", "POSITIVE_FEEDBACK"
    );

    public ChatResponse processQuestion(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String userId = request.getUserId();
        String userRole = request.getUserRole();
        String question = request.getQuestion();
        String sessionToken = request.getSessionId();

        log.info("🤖 HYBRID CHATBOT - Session: {}, User: {} ({}), Question: {}",
                sessionToken, userId, userRole, question);

        try {
            // ========== SESSION MANAGEMENT ==========
            // 1. Validate or create session
            Optional<ChatSession> sessionOpt = sessionService.getSession(sessionToken);

            if (sessionOpt.isEmpty()) {
                // Session doesn't exist or is expired, create new one
                ChatSession newSession = sessionService.createSession(userId, userRole);
                sessionToken = newSession.getSessionToken();
                request.setSessionId(sessionToken);
                log.info("✅ Created new session: {} for user: {}", sessionToken, userId);
            } else {
                // Session exists, extend it
                sessionService.extendSession(sessionToken);
                log.debug("Extended session: {}", sessionToken);
            }

            // 2. Update session context with current page and question
            Map<String, Object> contextUpdate = new HashMap<>();
            if (request.getContext() != null && request.getContext().containsKey("currentPage")) {
                contextUpdate.put("currentPage", request.getContext().get("currentPage"));
            }
            contextUpdate.put("lastQuestion", question);
            contextUpdate.put("lastActivityTime", LocalDateTime.now().toString());
            sessionService.updateSessionContext(sessionToken, contextUpdate);

            // 3. Save user message to session
            sessionService.addMessage(sessionToken, question, true, null, null, 0, null);

            // ========== ACCESS CONTROL ==========
            String authToken = extractAuthToken();
            AccessControlService.AccessResult accessCheck =
                    accessControl.checkNoteAccess(question, userId, userRole, authToken);

            if (!accessCheck.hasAccess && accessCheck.noteId != null) {
                ChatResponse response = buildAccessDeniedResponse(request, accessCheck);
                // Save bot response to session
                sessionService.addMessage(sessionToken, response.getAnswer(), false,
                        "ACCESS_DENIED", null, (int)(System.currentTimeMillis() - startTime), null);
                response.setSessionId(sessionToken);
                return response;
            }

            // ========== MANAGER DETECTION (BEFORE CLASSIFICATION) ==========
            String lowerQuestion = question.toLowerCase();

            if ("MANAGER".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
                if (lowerQuestion.contains("toutes les notes") ||
                        lowerQuestion.contains("notes du département")) {
                    log.info("📊 Détection MANAGER: toutes les notes du département");
                    ChatResponse response = simpleChatbotService.processQuestion(request);
                    // Save bot response
                    sessionService.addMessage(sessionToken, response.getAnswer(), false,
                            "MANAGER_ACTIONS", null, (int)(System.currentTimeMillis() - startTime),
                            response.getQuickReplies() != null ? convertQuickRepliesToJson(response.getQuickReplies()) : null);
                    response.setSessionId(sessionToken);
                    return response;
                }

                if (lowerQuestion.contains("notes en attente") ||
                        lowerQuestion.contains("en attente de validation") ||
                        lowerQuestion.contains("notes à valider")) {
                    log.info("📊 Détection MANAGER: notes en attente");
                    ChatResponse response = simpleChatbotService.processQuestion(request);
                    sessionService.addMessage(sessionToken, response.getAnswer(), false,
                            "MANAGER_ACTIONS", null, (int)(System.currentTimeMillis() - startTime),
                            response.getQuickReplies() != null ? convertQuickRepliesToJson(response.getQuickReplies()) : null);
                    response.setSessionId(sessionToken);
                    return response;
                }

                if (lowerQuestion.contains("mes notes")) {
                    log.info("📊 Détection MANAGER: mes notes (manager) → toutes les notes du département");
                    ChatResponse response = simpleChatbotService.processQuestion(request);
                    sessionService.addMessage(sessionToken, response.getAnswer(), false,
                            "MANAGER_ACTIONS", null, (int)(System.currentTimeMillis() - startTime),
                            response.getQuickReplies() != null ? convertQuickRepliesToJson(response.getQuickReplies()) : null);
                    response.setSessionId(sessionToken);
                    return response;
                }
            }

            // ========== INTENT CLASSIFICATION ==========
            IntentClassifierService.ClassificationResult classification =
                    intentClassifier.classify(question, userRole, accessCheck.noteId != null);

            log.info("🎯 Classification: type={}, intent={}, confiance={}",
                    classification.type, classification.intent, classification.confidence);

            // ========== ROUTING DECISION ==========
            ChatResponse response;

            // Social intents - handle directly
            if (SOCIAL_INTENTS.contains(classification.intent)) {
                log.info("🤝 Intent social détecté: {}, génération de réponse directe", classification.intent);
                response = handleSocialIntentDirectly(classification.intent, request);
            }
            // Rules - static responses
            else if ("RULES".equals(classification.type)) {
                log.info("➡️ Routage vers RÈGLES STATIQUES (réponse directe)");
                String rulesAnswer = rulesDataService.answerQuestion(question);
                if (rulesAnswer != null && !rulesAnswer.isEmpty()) {
                    response = buildRulesResponse(rulesAnswer, request, startTime);
                } else {
                    response = buildRulesResponse(rulesDataService.getStaticRulesOnly(), request, startTime);
                }
            }
            // Simple chatbot for simple questions with high confidence
            else if (classification.isSimple() && classification.confidence >= simpleConfidenceThreshold) {
                log.info("➡️ Routage vers CHATBOT SIMPLE (confiance: {})", classification.confidence);
                response = simpleChatbotService.processQuestion(request);

                if (!isSimpleResponseAdequate(response, question) && fallbackToSmart) {
                    log.info("⚠️ Fallback vers SMART chatbot (réponse simple insuffisante)");
                    response = smartChatbotService.processSmartQuestion(request);
                }
            }
            // Smart chatbot for complex questions
            else {
                log.info("➡️ Routage vers CHATBOT SMART (type: {}, confiance: {})",
                        classification.type, classification.confidence);
                response = smartChatbotService.processSmartQuestion(request);
            }

            // ========== SAVE BOT RESPONSE TO SESSION ==========
            long duration = System.currentTimeMillis() - startTime;
            sessionService.addMessage(
                    sessionToken,
                    response.getAnswer(),
                    false,
                    classification.intent,
                    classification.confidence,
                    (int) duration,
                    response.getQuickReplies() != null ? convertQuickRepliesToJson(response.getQuickReplies()) : null
            );

            // ========== SET SESSION ID IN RESPONSE ==========
            response.setSessionId(sessionToken);

            log.info("✅ Réponse générée en {} ms pour la session {}", duration, sessionToken);
            return response;

        } catch (Exception e) {
            log.error("❌ Erreur dans le chatbot hybride: {}", e.getMessage(), e);
            return handleError(e, request, startTime);
        }
    }

    /**
     * Handle social intents directly without going through the simple chatbot service
     */
    private ChatResponse handleSocialIntentDirectly(String intent, ChatRequest request) {
        Random random = new Random();
        String response;

        switch (intent) {
            case "GREETING":
                List<String> greetings = Arrays.asList(
                        "Bonjour ! 👋 Comment puis-je vous aider aujourd'hui ?",
                        "Bonjour ! 😊 Je suis votre assistant Coral.io. Comment puis-je vous assister ?",
                        "Bonjour et bienvenue ! 🌟 N'hésitez pas à me poser des questions sur vos notes de frais.",
                        "Salut ! 👋 Je suis là pour vous aider avec vos demandes de notes de frais.",
                        "Bonjour ! ☀️ Que puis-je faire pour vous aujourd'hui ?"
                );
                response = greetings.get(random.nextInt(greetings.size()));
                break;

            case "GRATITUDE":
                List<String> gratitudes = Arrays.asList(
                        "Avec plaisir ! 😊 N'hésitez pas si vous avez d'autres questions.",
                        "Je vous en prie ! 🙏 Je suis là pour vous aider.",
                        "C'est un plaisir ! ✨ Si vous avez besoin d'autre chose, je suis disponible.",
                        "Merci à vous ! 🌟 Puis-je faire autre chose pour vous ?",
                        "De rien ! 😊 Je suis ravi de pouvoir vous aider."
                );
                response = gratitudes.get(random.nextInt(gratitudes.size()));
                break;

            case "FAREWELL":
                List<String> farewells = Arrays.asList(
                        "Au revoir ! 👋 À bientôt sur Coral.io !",
                        "Bonne journée ! 🌟 N'hésitez pas à revenir si vous avez besoin d'aide.",
                        "À bientôt ! 😊 Prenez soin de vous.",
                        "Salut ! 👋 Je vous souhaite une excellente journée.",
                        "À la prochaine ! 👋 Bonne continuation."
                );
                response = farewells.get(random.nextInt(farewells.size()));
                break;

            case "POSITIVE_FEEDBACK":
                List<String> feedbacks = Arrays.asList(
                        "😊 Merci beaucoup ! Je suis ravi de pouvoir vous aider.",
                        "🎉 Génial ! Je suis content que cela vous plaise.",
                        "🌟 Merci ! N'hésitez pas si vous avez besoin d'autre chose.",
                        "😄 Super ! Je suis là pour vous aider avec plaisir.",
                        "✨ C'est formidable ! Je suis heureux de vous assister."
                );
                response = feedbacks.get(random.nextInt(feedbacks.size()));
                break;

            default:
                response = "Je suis là pour vous aider ! 😊";
        }

        String formattedResponse = responseFormatter.formatResponse(response, intent, request.getUserRole());

        return ChatResponse.builder()
                .answer(formattedResponse)
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .responseType("social")
                .quickReplies(generateDefaultQuickReplies())
                .build();
    }

    /**
     * Convert quick replies to JSON string for storage
     */
    private String convertQuickRepliesToJson(List<QuickReply> quickReplies) {
        if (quickReplies == null || quickReplies.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(quickReplies);
        } catch (Exception e) {
            log.warn("Error converting quick replies to JSON", e);
            return "[]";
        }
    }

    private List<QuickReply> generateDefaultQuickReplies() {
        return List.of(
                QuickReply.builder().text("📋 Mes notes").payload("VIEW_NOTES").icon("📋").build(),
                QuickReply.builder().text("💰 Plafonds").payload("CATEGORY_PLAFOND").icon("💰").build(),
                QuickReply.builder().text("📝 Créer une note").payload("CREATE_NOTE").icon("📝").build(),
                QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
        );
    }

    private ChatResponse buildRulesResponse(String answer, ChatRequest request, long startTime) {
        String formattedAnswer = PromptTemplates.formatRulesAnswer(answer, request.getUserRole());

        return ChatResponse.builder()
                .answer(formattedAnswer)
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(generateRulesQuickReplies())
                .responseType("rules")
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private List<QuickReply> generateRulesQuickReplies() {
        return List.of(
                QuickReply.builder().text("📋 Plafonds").payload("CATEGORY_PLAFOND").icon("💰").build(),
                QuickReply.builder().text("📎 Justificatifs").payload("VALIDATION_RULES").icon("📎").build(),
                QuickReply.builder().text("🔄 Workflow").payload("WORKFLOW").icon("🔄").build(),
                QuickReply.builder().text("🚨 Alertes").payload("ALERT_RULES").icon("⚠️").build(),
                QuickReply.builder().text("❓ FAQ").payload("FAQ").icon("❓").build()
        );
    }

    private String extractAuthToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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

    private boolean isSimpleResponseAdequate(ChatResponse response, String question) {
        if (response == null || response.getAnswer() == null) {
            return false;
        }

        String answer = response.getAnswer().toLowerCase();
        String lowerQuestion = question.toLowerCase();

        boolean isRulesQuestion = lowerQuestion.contains("règle") ||
                lowerQuestion.contains("règles") ||
                lowerQuestion.contains("justificatif") ||
                lowerQuestion.contains("format") ||
                lowerQuestion.contains("taille") ||
                lowerQuestion.contains("plafond") ||
                lowerQuestion.contains("remboursement") ||
                lowerQuestion.contains("workflow") ||
                lowerQuestion.contains("validation") ||
                lowerQuestion.contains("alerte") ||
                lowerQuestion.contains("faq") ||
                lowerQuestion.contains("jours fériés") ||
                lowerQuestion.contains("devise");

        if (isRulesQuestion) {
            boolean hasSorryPattern = answer.contains("désolé") ||
                    answer.contains("je n'ai pas") ||
                    answer.contains("je ne peux pas");
            boolean hasRelevantContent = answer.length() > 10;
            return !hasSorryPattern && hasRelevantContent;
        }

        boolean isPersonalNotesQuestion = lowerQuestion.contains("mes notes") ||
                lowerQuestion.contains("ma note") ||
                lowerQuestion.contains("mes dépenses") ||
                lowerQuestion.contains("mes frais");

        if (isPersonalNotesQuestion) {
            boolean hasNotes = answer.contains("note") ||
                    answer.contains("dépense") ||
                    answer.contains("frais") ||
                    answer.contains("aucune note");
            boolean isError = answer.contains("erreur") ||
                    answer.contains("désolé") && answer.contains("pas");
            return hasNotes || (!isError && answer.length() > 20);
        }

        boolean isPlafondQuestion = lowerQuestion.contains("plafond") ||
                lowerQuestion.contains("catégorie");

        if (isPlafondQuestion) {
            boolean hasPlafondInfo = answer.contains("plafond") ||
                    answer.contains("tnd") ||
                    answer.contains("limite");
            return hasPlafondInfo && answer.length() > 20;
        }

        if (answer.contains("authentifié") ||
                answer.contains("token") ||
                answer.contains("session") ||
                answer.contains("reconnecter")) {
            return true;
        }

        List<String> inadequatePatterns = Arrays.asList(
                "désolé", "je n'ai pas", "je ne peux pas", "erreur",
                "compris", "pas clair", "réessayer"
        );

        for (String pattern : inadequatePatterns) {
            if (answer.contains(pattern)) {
                return false;
            }
        }

        boolean questionHasNote = lowerQuestion.contains("note");
        boolean answerHasNote = answer.contains("note");

        if (questionHasNote && !answerHasNote) {
            return false;
        }

        return answer.length() > 20;
    }

    private ChatResponse buildAccessDeniedResponse(ChatRequest request,
                                                   AccessControlService.AccessResult accessCheck) {
        String message;

        if (accessCheck.noteId != null) {
            switch (accessCheck.code) {
                case "NOT_FOUND":
                    message = "❌ **Note introuvable**\n\n" +
                            "La note #" + accessCheck.noteId + " n'existe pas.\n\n" +
                            "💡 **Vérifiez le numéro** ou dites 'mes notes' pour voir la liste de vos notes.";
                    break;
                case "NOT_OWNER":
                    message = "❌ **Accès refusé**\n\n" +
                            "Cette note appartient à un autre employé.\n\n" +
                            "💡 **Conseil :** Dites 'mes notes' pour voir la liste de vos notes.";
                    break;
                case "MANAGER_NO_DEPARTMENT":
                    message = "❌ **Erreur**\n\n" +
                            "Vous n'avez pas de département assigné en tant que manager.\n\n" +
                            "💡 Contactez votre administrateur pour corriger ce problème.";
                    break;
                case "PROJECT_NOT_FOUND":
                    message = "❌ **Erreur**\n\n" +
                            "Le projet associé à cette note n'a pas été trouvé.\n\n" +
                            "💡 Vérifiez que le projet existe toujours.";
                    break;
                case "WRONG_DEPARTMENT":
                    message = "❌ **Accès refusé**\n\n" +
                            "Cette note appartient à un projet d'un autre département.\n\n" +
                            "💡 Vous ne pouvez consulter que les notes de votre département.";
                    break;
                default:
                    message = "❌ **Accès non autorisé**\n\n" +
                            "Vous n'êtes pas autorisé à consulter cette note.\n\n" +
                            "💡 Vérifiez vos droits d'accès.";
            }
        } else {
            message = "❌ **Accès non autorisé**\n\n" +
                    "Vous n'êtes pas autorisé à effectuer cette action.";
        }

        // ✅ NE PAS appeler formatErrorResponse !
        // Le message est déjà formaté correctement

        return ChatResponse.builder()
                .answer(message)  // ← Directement le message formaté
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(List.of(
                        QuickReply.builder().text("📋 Mes notes").payload("VIEW_NOTES").icon("📋").build(),
                        QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                ))
                .responseType("error")
                .build();
    }

    private ChatResponse handleError(Exception e, ChatRequest request, long startTime) {
        String errorMessage = e.getMessage();

        if (errorMessage != null && (errorMessage.contains("401") ||
                errorMessage.contains("UNAUTHORIZED") ||
                errorMessage.contains("authentifié"))) {
            log.warn("🔒 Erreur d'authentification pour l'utilisateur: {}", request.getUserId());

            String formattedError = responseFormatter.formatErrorResponse("authentifié", request.getUserRole());

            return ChatResponse.builder()
                    .answer(formattedError)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("error")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        if (errorMessage != null && errorMessage.contains("timeout")) {
            log.warn("⏱️ Timeout lors du traitement");

            String formattedError = responseFormatter.formatErrorResponse("timeout", request.getUserRole());

            return ChatResponse.builder()
                    .answer(formattedError)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("error")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            log.info("🔄 Tentative de fallback vers le simple chatbot");
            return simpleChatbotService.processQuestion(request);
        } catch (Exception fallbackError) {
            log.error("❌ Fallback échoué également: {}", fallbackError.getMessage());

            String formattedError = responseFormatter.formatErrorResponse("technique", request.getUserRole());

            return ChatResponse.builder()
                    .answer(formattedError)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("error")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
}