package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

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

    @Value("${chatbot.hybrid.simple-confidence-threshold:0.6}")
    private double simpleConfidenceThreshold;

    @Value("${chatbot.hybrid.fallback-to-smart:true}")
    private boolean fallbackToSmart;

    public ChatResponse processQuestion(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String userId = request.getUserId();
        String userRole = request.getUserRole();
        String question = request.getQuestion();

        // Récupérer le token JWT de la requête
        String authToken = extractAuthToken();

        log.info("🤖 HYBRID CHATBOT - User: {} ({}), Question: {}", userId, userRole, question);

        try {
            // 1. VÉRIFICATION DES DROITS D'ACCÈS
            AccessControlService.AccessResult accessCheck =
                    accessControl.checkNoteAccess(question, userId, userRole, authToken);

            if (!accessCheck.hasAccess && accessCheck.noteId != null) {
                // Accès refusé à une note spécifique
                return buildAccessDeniedResponse(request, accessCheck);
            }

            // 2. CLASSIFICATION DE L'INTENTION
            IntentClassifierService.ClassificationResult classification =
                    intentClassifier.classify(question, userRole, accessCheck.noteId != null);

            log.info("🎯 Classification: type={}, intent={}, confiance={}",
                    classification.type, classification.intent, classification.confidence);

            // 3. DÉCISION DU ROUTAGE
            ChatResponse response;

            if (classification.isSimple() && classification.confidence >= simpleConfidenceThreshold) {
                // ROUTAGE VERS CHATBOT SIMPLE
                log.info("➡️ Routage vers CHATBOT SIMPLE (confiance: {})", classification.confidence);
                response = simpleChatbotService.processQuestion(request);

                // Vérifier si la réponse du simple est satisfaisante
                if (isSimpleResponseAdequate(response, question)) {
                    log.info("✅ Réponse simple adéquate");
                } else if (fallbackToSmart) {
                    log.info("⚠️ Fallback vers SMART chatbot (réponse simple insuffisante)");
                    response = smartChatbotService.processSmartQuestion(request);
                }
            } else {
                // ROUTAGE VERS CHATBOT SMART
                log.info("➡️ Routage vers CHATBOT SMART (type: {}, confiance: {})",
                        classification.type, classification.confidence);
                response = smartChatbotService.processSmartQuestion(request);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Réponse générée en {} ms", duration);

            return response;

        } catch (Exception e) {
            log.error("❌ Erreur dans le chatbot hybride: {}", e.getMessage(), e);

            // ✅ Vérifier si c'est une erreur 401
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("401") ||
                    errorMessage.contains("UNAUTHORIZED") ||
                    errorMessage.contains("authentifié"))) {
                return ChatResponse.builder()
                        .answer("🔒 Vous n'êtes pas authentifié. Veuillez vous reconnecter.")
                        .sessionId(request.getSessionId())
                        .timestamp(java.time.LocalDateTime.now())
                        .responseType("error")
                        .build();
            }

            // Fallback vers smart en cas d'erreur
            log.info("⚠️ Fallback d'urgence vers SMART chatbot");
            return smartChatbotService.processSmartQuestion(request);
        }
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

        // ✅ Vérifier si la réponse est une erreur d'authentification
        if (answer.contains("authentifié") ||
                answer.contains("token") ||
                answer.contains("session") ||
                answer.contains("reconnecter")) {
            // C'est une réponse d'erreur valide, on la garde
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

        boolean questionHasNote = question.toLowerCase().contains("note");
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
                    message = String.format("La note #%d n'existe pas.", accessCheck.noteId);
                    break;
                case "NOT_OWNER":
                    message = "Désolé, cette note ne vous appartient pas.";
                    break;
                case "MANAGER_NO_DEPARTMENT":
                    message = "Vous n'avez pas de département assigné en tant que manager.";
                    break;
                case "PROJECT_NOT_FOUND":
                    message = "Le projet associé à cette note n'a pas été trouvé.";
                    break;
                case "WRONG_DEPARTMENT":
                    message = "Cette note appartient à un projet d'un autre département.";
                    break;
                default:
                    message = "Vous n'êtes pas autorisé à consulter cette note.";
            }
        } else {
            message = "Accès non autorisé.";
        }

        return ChatResponse.builder()
                .answer(message)
                .sessionId(request.getSessionId())
                .timestamp(java.time.LocalDateTime.now())
                .responseType("error")
                .build();
    }
}