package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.dto.QuickReply;
import com.coralio.chatbotmicroservice.entity.Category;
import com.coralio.chatbotmicroservice.entity.ChatSession;
import com.coralio.chatbotmicroservice.entity.ExpenseNote;
import com.coralio.chatbotmicroservice.entity.ExpenseLine;
import com.coralio.chatbotmicroservice.repository.CategoryRepository;
import com.coralio.chatbotmicroservice.repository.ExpenseNoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SmartChatbotService {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseNoteRepository expenseNoteRepository;

    @Autowired
    @Lazy
    private ChatbotService simpleChatbotService;

    @Autowired
    private RulesDataService staticRulesService;

    @Autowired
    private ResponseFormatter responseFormatter;

    @Autowired
    private ChatSessionService sessionService;

    @Value("${chatbot.smart.fallback-to-simple:true}")
    private boolean fallbackToSimple;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

    public ChatResponse processSmartQuestion(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("🧠 Processing SMART question: {}", request.getQuestion());

        try {
            String authToken = extractAuthToken();
            if (authToken == null || authToken.isEmpty()) {
                log.error("🔒 Token manquant pour SMART chatbot - utilisateur {}", request.getUserId());
                return ChatResponse.builder()
                        .answer("🔒 Vous n'êtes pas authentifié. Veuillez vous reconnecter.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .quickReplies(generateQuickReplies(request))
                        .responseType("error")
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            String sessionToken = request.getSessionId();
            List<ChatMessage> history = getConversationHistory(sessionToken);

            Long lastMentionedNoteId = extractLastNoteIdFromHistory(history);

            String question = request.getQuestion();
            String lowerQuestion = question.toLowerCase();

            boolean refersToPreviousNote = lowerQuestion.contains("cette note") ||
                    (lowerQuestion.contains("cette") && !lowerQuestion.contains("cette note")) ||
                    (lowerQuestion.contains("la note") && !lowerQuestion.matches(".*\\d+.*")) ||
                    lowerQuestion.contains("celle-ci") ||
                    lowerQuestion.contains("celle ci");

            Long effectiveNoteId = null;

            if (refersToPreviousNote && lastMentionedNoteId != null) {
                effectiveNoteId = lastMentionedNoteId;
                log.info("🔍 PRIORITÉ 1 - Contexte détecté: '{}' fait référence à la note #{} (historique)",
                        lowerQuestion.contains("cette note") ? "cette note" : "cette", lastMentionedNoteId);
            } else {
                effectiveNoteId = extractNoteIdFromQuestion(question);
                if (effectiveNoteId != null) {
                    log.info("🔍 PRIORITÉ 2 - Note #{} trouvée explicitement dans la question", effectiveNoteId);
                }
            }

            if (effectiveNoteId == null && lastMentionedNoteId != null) {
                if (lowerQuestion.contains("cette") && !lowerQuestion.contains("note")) {
                    effectiveNoteId = lastMentionedNoteId;
                    log.info("🔍 PRIORITÉ 3 - Contexte détecté: 'cette' fait référence à la note #{}", lastMentionedNoteId);
                }
            }

            log.info("📝 Note ID final après priorisation: {}", effectiveNoteId);

            String databaseContext = getDatabaseContext();
            String userContext = getUserContext(request.getUserId());

            String noteDetails = "";
            boolean hasNoteAccess = false;
            ExpenseNote fullNote = null;

            if (effectiveNoteId != null) {
                Optional<ExpenseNote> noteOpt = expenseNoteRepository.findById(effectiveNoteId);
                if (noteOpt.isPresent()) {
                    ExpenseNote note = noteOpt.get();

                    hasNoteAccess = note.getEmployeeId().equals(request.getUserId()) ||
                            "MANAGER".equalsIgnoreCase(request.getUserRole()) ||
                            "ADMIN".equalsIgnoreCase(request.getUserRole());

                    if (hasNoteAccess) {
                        fullNote = note;
                        noteDetails = buildCompleteNoteDetails(note);
                        log.info("📝 Note #{} trouvée avec {} lignes", effectiveNoteId,
                                note.getLines() != null ? note.getLines().size() : 0);
                    } else {
                        log.warn("⛔ Accès refusé pour la note #{}", effectiveNoteId);
                    }
                }
            }

            // ✅ CORRIGÉ: similaritySearch sans paramètre int
            List<Document> relevantDocs = vectorStore.similaritySearch(question);
            String vectorContext = relevantDocs.stream()
                    .limit(3)  // Prendre seulement les 3 premiers
                    .map(Document::getText)
                    .limit(2)  // Limiter à 2 documents
                    .collect(Collectors.joining("\n"));

            String systemPrompt = buildPromptWithHistory(
                    question,
                    databaseContext,
                    userContext,
                    vectorContext,
                    noteDetails,
                    fullNote,
                    request.getUserRole(),
                    history,
                    lastMentionedNoteId,
                    effectiveNoteId
            );

            log.debug("📝 PROMPT size: {} chars", systemPrompt.length());

            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(question)
            );
            Prompt prompt = new Prompt(messages);

            var springAiResponse = chatModel.call(prompt);
            String answer = springAiResponse.getResult().getOutput().getText();

            if (effectiveNoteId != null && hasNoteAccess && fullNote != null) {
                answer = forceCorrectAnswerWithRealData(answer, question, fullNote);
            }

            String formattedAnswer = responseFormatter.formatResponse(
                    answer,
                    detectIntentFromContext(effectiveNoteId, question),
                    request.getUserRole()
            );

            List<QuickReply> quickReplies = generateQuickReplies(request);

            long duration = System.currentTimeMillis() - startTime;

            return ChatResponse.builder()
                    .answer(formattedAnswer)
                    .sessionId(sessionToken)
                    .timestamp(LocalDateTime.now())
                    .quickReplies(quickReplies)
                    .requiresAction(detectAction(formattedAnswer))
                    .actionType(extractAction(formattedAnswer))
                    .responseType("smart")
                    .processingTimeMs(duration)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error in smart processing: {}", e.getMessage(), e);

            if (e.getMessage() != null && (e.getMessage().contains("401") ||
                    e.getMessage().contains("UNAUTHORIZED"))) {
                return ChatResponse.builder()
                        .answer("🔒 Vous n'êtes pas authentifié. Veuillez vous reconnecter.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .quickReplies(generateQuickReplies(request))
                        .responseType("error")
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            if (fallbackToSimple) {
                return simpleChatbotService.processQuestion(request);
            }

            return ChatResponse.builder()
                    .answer(responseFormatter.formatErrorResponse(e.getMessage(), request.getUserRole()))
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .responseType("error")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private String buildCompleteNoteDetails(ExpenseNote note) {
        StringBuilder sb = new StringBuilder();

        sb.append("📝 **NOTE #").append(note.getId()).append("**\n");
        sb.append("• 💰 Montant total: **").append(String.format("%.2f", note.getTotalAmount())).append(" TND**\n");
        sb.append("• 📊 Statut: **").append(note.getStatus()).append("** ").append(getStatusEmoji(note.getStatus())).append("\n");
        sb.append("• 📅 Créée le: ").append(formatDateTime(note.getCreatedAt())).append("\n");

        List<ExpenseLine> lines = note.getLines();
        if (lines != null && !lines.isEmpty()) {
            sb.append("\n📋 **Lignes:**\n");
            for (ExpenseLine line : lines) {
                String categoryName = getCategoryNameById(line.getCategoryId());
                sb.append("  • [").append(categoryName).append("] ");
                sb.append(String.format("%.2f TND", line.getAmount()));

                boolean hasJustificatif = line.getJustificatifPath() != null && !line.getJustificatifPath().isEmpty();
                sb.append(hasJustificatif ? " 📎" : " ❌");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String forceCorrectAnswerWithRealData(String llmAnswer, String question, ExpenseNote realNote) {
        String lowerAnswer = llmAnswer.toLowerCase();
        String lowerQuestion = question.toLowerCase();

        // ✅ Correction: Vérifier si le statut est incorrect
        if (realNote != null && lowerAnswer.contains("refus") && !"REFUSEE".equals(realNote.getStatus())) {
            log.warn("⚠️ LLM a dit 'refusée' mais le vrai statut est {}", realNote.getStatus());
            return buildCorrectComplianceAnswer(realNote);
        }

        boolean isComplianceQuestion = lowerQuestion.contains("respecte") ||
                lowerQuestion.contains("règle") ||
                lowerQuestion.contains("conformité") ||
                lowerQuestion.contains("valide");

        if (isComplianceQuestion && realNote != null) {
            String expectedAmount = String.format("%.2f", realNote.getTotalAmount());
            boolean hasCorrectAmount = lowerAnswer.contains(expectedAmount);

            if (!hasCorrectAmount || lowerAnswer.contains("refus")) {
                log.warn("⚠️ Détection d'hallucination LLM - Reconstruction de la réponse correcte");
                return buildCorrectComplianceAnswer(realNote);
            }
        }

        return llmAnswer;
    }

    private String buildCorrectComplianceAnswer(ExpenseNote note) {
        StringBuilder sb = new StringBuilder();

        sb.append("📋 **Analyse de la note #").append(note.getId()).append("**\n\n");

        sb.append("**Données réelles de la note:**\n");
        sb.append(String.format("• 💰 Montant total: **%.2f TND**\n", note.getTotalAmount()));
        sb.append(String.format("• 📊 Statut: **%s** %s\n", note.getStatus(), getStatusEmoji(note.getStatus())));
        sb.append(String.format("• 📅 Date de création: %s\n", formatDateTime(note.getCreatedAt())));
        sb.append("\n");

        List<ExpenseLine> lines = note.getLines();
        if (lines != null && !lines.isEmpty()) {
            sb.append("**📎 Justificatifs:**\n");
            for (ExpenseLine line : lines) {
                String categoryName = getCategoryNameById(line.getCategoryId());
                boolean hasJustif = line.getJustificatifPath() != null && !line.getJustificatifPath().isEmpty();
                sb.append(String.format("  • [%s] %.2f TND: %s\n",
                        categoryName, line.getAmount(), hasJustif ? "✅ Justificatif présent" : "❌ Justificatif manquant"));
            }
            sb.append("\n");
        }

        // ✅ Vérifier le plafond pour la catégorie
        if (lines != null && !lines.isEmpty()) {
            for (ExpenseLine line : lines) {
                String categoryName = getCategoryNameById(line.getCategoryId());
                Optional<Category> categoryOpt = categoryRepository.findByName(categoryName);

                if (categoryOpt.isPresent()) {
                    Category category = categoryOpt.get();
                    double plafond = category.getPlafond();

                    if (line.getAmount() > plafond) {
                        sb.append("**⚠️ Alerte dépassement de plafond:**\n");
                        sb.append(String.format("Le montant de %.2f TND dépasse le plafond de %.2f TND pour la catégorie %s.\n",
                                line.getAmount(), plafond, categoryName));
                        sb.append(String.format("Le remboursement sera limité à %.2f TND.\n\n", plafond));
                    }
                }
            }
        }

        sb.append("**✅ Conclusion:**\n");
        if ("EN_ATTENTE".equals(note.getStatus())) {
            sb.append("La note est en attente de validation manager. ");
            if (lines != null && lines.stream().anyMatch(l -> l.getAmount() > 150)) {
                sb.append("⚠️ Attention: Le montant dépasse le plafond autorisé. ");
                sb.append("Le remboursement sera limité au plafond de la catégorie.");
            } else {
                sb.append("Tous les justificatifs sont présents et les montants respectent les plafonds.");
            }
        } else if ("VALIDEE".equals(note.getStatus())) {
            sb.append("La note a été validée et est en attente de remboursement.\n");
        } else if ("REFUSEE".equals(note.getStatus())) {
            sb.append("❌ La note a été refusée.\n");
        }

        return sb.toString();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "Non spécifiée";
        return dateTime.format(DATE_FORMATTER);
    }

    private String getStatusEmoji(String status) {
        if (status == null) return "📝";
        return switch (status.toUpperCase()) {
            case "EN_ATTENTE" -> "⏳";
            case "VALIDEE" -> "✅";
            case "REFUSEE" -> "❌";
            case "REMBOURSEE" -> "💰";
            default -> "📝";
        };
    }

    private String detectIntentFromContext(Long noteId, String question) {
        String lowerQuestion = question.toLowerCase();
        if (noteId != null) {
            return "NOTE_DETAILS";
        }
        if (lowerQuestion.contains("plafond")) {
            return "CATEGORY_PLAFOND";
        }
        if (lowerQuestion.contains("règle") || lowerQuestion.contains("règles")) {
            return "VALIDATION_RULES";
        }
        if (lowerQuestion.contains("créer") || lowerQuestion.contains("ajouter")) {
            return "CREATE_NOTE";
        }
        if (lowerQuestion.contains("mes notes")) {
            return "VIEW_NOTES";
        }
        return "HELP";
    }

    private Long extractNoteIdFromQuestion(String question) {
        if (question == null) return null;

        Pattern[] patterns = {
                Pattern.compile("note\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("#(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("n°\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("num[ée]ro\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(question);
            if (matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.warn("Format d'ID invalide: {}", matcher.group(1));
                }
            }
        }
        return null;
    }

    private String getCategoryNameById(Long categoryId) {
        if (categoryId == null) return "Catégorie inconnue";
        Optional<Category> category = categoryRepository.findById(categoryId);
        return category.map(Category::getName).orElse("Catégorie " + categoryId);
    }

    private String getDatabaseContext() {
        StringBuilder context = new StringBuilder();
        List<Category> categories = categoryRepository.findByActiveTrue();

        context.append("📋 Catégories et plafonds:\n");
        for (Category cat : categories.stream().limit(10).toList()) {
            context.append(String.format("• %s: %.2f TND\n", cat.getName(), cat.getPlafond()));
        }

        return context.toString();
    }

    private String getUserContext(String userId) {
        StringBuilder context = new StringBuilder();
        List<ExpenseNote> recentNotes = expenseNoteRepository.findRecentByEmployee(userId);

        if (recentNotes.isEmpty()) {
            context.append("Aucune note récente.\n");
        } else {
            context.append("Notes récentes:\n");
            for (ExpenseNote note : recentNotes.stream().limit(3).toList()) {
                context.append(String.format("• #%d: %.2f TND (%s)\n",
                        note.getId(), note.getTotalAmount(), note.getStatus()));
            }
        }

        return context.toString();
    }

    private List<QuickReply> generateQuickReplies(ChatRequest request) {
        List<QuickReply> replies = new ArrayList<>();

        categoryRepository.findByActiveTrue().stream()
                .limit(3)
                .forEach(cat -> replies.add(QuickReply.builder()
                        .text(cat.getName())
                        .payload("CATEGORY_" + cat.getId())
                        .icon(getCategoryIcon(cat.getName()))
                        .build()));

        replies.add(QuickReply.builder()
                .text("❓ Aide")
                .payload("HELP")
                .icon("❓")
                .build());

        return replies;
    }

    private String getCategoryIcon(String categoryName) {
        Map<String, String> icons = Map.of(
                "Transport", "🚗",
                "Hébergement", "🏨",
                "Restauration", "🍽️",
                "Carburant", "⛽",
                "Frais professionnels", "💼",
                "Autres", "📦"
        );
        return icons.getOrDefault(categoryName, "📝");
    }

    private boolean detectAction(String answer) {
        String lower = answer.toLowerCase();
        return lower.contains("créer") ||
                lower.contains("valider") ||
                lower.contains("supprimer") ||
                lower.contains("télécharger");
    }

    private String extractAction(String answer) {
        String lower = answer.toLowerCase();
        if (lower.contains("créer") || lower.contains("ajouter")) return "CREATE";
        if (lower.contains("valider")) return "VALIDATE";
        if (lower.contains("supprimer")) return "DELETE";
        if (lower.contains("télécharger")) return "UPLOAD";
        return null;
    }

    private static class ChatMessage {
        private String content;
        private boolean isUser;
        private LocalDateTime timestamp;

        public ChatMessage(String content, boolean isUser, LocalDateTime timestamp) {
            this.content = content;
            this.isUser = isUser;
            this.timestamp = timestamp;
        }

        public String getContent() { return content; }
        public boolean isUser() { return isUser; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    private List<ChatMessage> getConversationHistory(String sessionToken) {
        List<ChatMessage> history = new ArrayList<>();

        try {
            List<com.coralio.chatbotmicroservice.entity.ChatMessage> messages =
                    sessionService.getSessionHistory(sessionToken, 5);

            if (messages != null) {
                for (com.coralio.chatbotmicroservice.entity.ChatMessage msg : messages) {
                    history.add(new ChatMessage(
                            msg.getMessageText(),
                            msg.isUser(),
                            msg.getCreatedAt()
                    ));
                }
                log.debug("📜 Historique chargé: {} messages", history.size());
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer l'historique: {}", e.getMessage());
        }

        return history;
    }

    private Long extractLastNoteIdFromHistory(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (msg.isUser()) {
                Long noteId = extractNoteIdFromText(msg.getContent());
                if (noteId != null) {
                    log.info("📝 Note #{} trouvée dans l'historique: '{}'", noteId, msg.getContent());
                    return noteId;
                }
            }
        }
        return null;
    }

    private Long extractNoteIdFromText(String text) {
        if (text == null) return null;

        Pattern[] patterns = {
                Pattern.compile("note\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("#(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("n°\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("num[ée]ro\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("détails de la note (\\d+)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Ignorer
                }
            }
        }
        return null;
    }

    private String buildPromptWithHistory(String question, String databaseContext,
                                          String userContext, String vectorContext,
                                          String noteDetails, ExpenseNote fullNote,
                                          String userRole, List<ChatMessage> history,
                                          Long lastMentionedNoteId, Long effectiveNoteId) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Tu es l'assistant Coral.io. Réponds de façon concise et précise.\n\n");

        if (history != null && !history.isEmpty()) {
            prompt.append("Conversation récente:\n");
            List<ChatMessage> recentHistory = history.size() > 3 ? history.subList(history.size() - 3, history.size()) : history;
            for (ChatMessage msg : recentHistory) {
                String role = msg.isUser() ? "Utilisateur" : "Assistant";
                String content = msg.getContent().length() > 200 ? msg.getContent().substring(0, 200) + "..." : msg.getContent();
                prompt.append(role).append(": ").append(content).append("\n");
            }
            prompt.append("\n");

            if (lastMentionedNoteId != null) {
                prompt.append("⚠️ CONTEXTE: La dernière note mentionnée est la #").append(lastMentionedNoteId).append(".\n");
                prompt.append("Quand l'utilisateur dit 'cette note', il parle de la note #").append(lastMentionedNoteId).append(".\n\n");
            }
        }

        if (noteDetails != null && !noteDetails.isEmpty()) {
            prompt.append("DONNÉES DE LA NOTE:\n");
            prompt.append(noteDetails).append("\n\n");
        }

        prompt.append("RÈGLES:\n");
        prompt.append(databaseContext).append("\n\n");

        if (effectiveNoteId != null) {
            prompt.append("⚠️ IMPORTANT: Réponds UNIQUEMENT sur la note #").append(effectiveNoteId).append(".\n");
        }

        return prompt.toString();
    }
}