package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.dto.QuickReply;
import com.coralio.chatbotmicroservice.entity.Category;
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
    private ChatbotService simpleChatbotService;

    @Autowired
    private RulesDataService staticRulesService;

    @Value("${chatbot.smart.fallback-to-simple:true}")
    private boolean fallbackToSimple;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Extrait le token de la requête
     */
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
            // ✅ Vérifier si le token est présent
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

            // 1. Database context
            String databaseContext = getDatabaseContext();

            // 2. User context
            String userContext = getUserContext(request.getUserId());

            // 3. Detect specific note
            Long noteId = extractNoteIdFromQuestion(request.getQuestion());
            String noteDetails = "";
            boolean hasNoteAccess = false;

            if (noteId != null) {
                noteDetails = getNoteDetails(noteId, request.getUserId(), request.getUserRole());
                hasNoteAccess = noteDetails != null && !noteDetails.isEmpty() &&
                        !noteDetails.contains("n'existe pas") &&
                        !noteDetails.contains("pas les droits");
                log.info("📝 Note spécifique détectée #{} - Access: {}", noteId, hasNoteAccess);
            }

            // 4. Vector search
            List<Document> relevantDocs = vectorStore.similaritySearch(request.getQuestion());
            String vectorContext = relevantDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            // 5. Build prompt
            String systemPrompt = buildSystemPrompt(databaseContext, userContext, noteDetails, vectorContext);
            log.debug("📝 PROMPT:\n{}", systemPrompt);

            // 6. LLM call
            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(request.getQuestion())
            );
            Prompt prompt = new Prompt(messages);
            var springAiResponse = chatModel.call(prompt);
            String answer = springAiResponse.getResult().getOutput().getText();

            // 7. POST-PROCESSING CORRECTION
            if (noteId != null && hasNoteAccess) {
                answer = forceCorrectAnswerForNoteQuestion(answer, request.getQuestion(), noteDetails, noteId);
            }

            // 8. Generate quick replies
            List<QuickReply> quickReplies = generateQuickReplies(request);

            long duration = System.currentTimeMillis() - startTime;

            // 9. Build response
            return ChatResponse.builder()
                    .answer(answer)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .quickReplies(quickReplies)
                    .requiresAction(detectAction(answer))
                    .actionType(extractAction(answer))
                    .responseType("smart")
                    .processingTimeMs(duration)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error in smart processing: {}", e.getMessage(), e);

            // ✅ Vérifier si l'erreur est une 401
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("401") ||
                    errorMessage.contains("UNAUTHORIZED") ||
                    errorMessage.contains("authentifié"))) {
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
                log.info("⚠️ Falling back to simple chatbot");
                return simpleChatbotService.processQuestion(request);
            }

            return buildErrorResponse(request, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * POST-PROCESSING: Force correct answer for note questions
     */
    private String forceCorrectAnswerForNoteQuestion(String answer, String question, String noteDetails, Long noteId) {
        if (noteId == null || noteDetails == null || noteDetails.isEmpty()) {
            return answer;
        }

        // Check if it's a question about categories
        boolean isCategoriesQuestion = question.toLowerCase().contains("catégorie") ||
                question.toLowerCase().contains("categories") ||
                question.toLowerCase().contains("ligne") ||
                question.toLowerCase().contains("lignes");

        List<String> refusalPatterns = Arrays.asList(
                "n'ai pas accès", "pas accès", "ne puis pas", "je ne peux pas",
                "cannot", "désolé", "confidentiel", "privé", "private",
                "informations personnelles", "ne peut pas", "impossible de",
                "refuse de", "je refuse", "pas autorisé", "not authorized",
                "je n'ai pas", "je ne peux"
        );

        String lowerAnswer = answer.toLowerCase();
        boolean hasRefusal = refusalPatterns.stream().anyMatch(lowerAnswer::contains);

        // Also check if answer only shows one line when there are multiple
        boolean showsOnlyOneLine = lowerAnswer.contains("ligne") &&
                !lowerAnswer.contains("ligne 2") &&
                !lowerAnswer.contains("ligne 3") &&
                noteDetails.contains("LIGNE 2");

        if ((hasRefusal || showsOnlyOneLine) && noteDetails.contains("LIGNES DE LA NOTE")) {
            log.warn("⚠️ Correcting LLM response for note #{}", noteId);

            if (isCategoriesQuestion) {
                String categories = extractAllCategoriesFromDetails(noteDetails);
                if (categories != null) {
                    return String.format("📊 Pour la note #%d, voici toutes les catégories utilisées :\n%s",
                            noteId, categories);
                }
            }
        }

        return answer;
    }

    private String extractAllCategoriesFromDetails(String noteDetails) {
        StringBuilder categories = new StringBuilder();
        Pattern pattern = Pattern.compile("• Ligne \\d+: ([^\\n]+)");
        Matcher matcher = pattern.matcher(noteDetails);

        boolean found = false;
        while (matcher.find()) {
            found = true;
            categories.append("  ").append(matcher.group(1).trim()).append("\n");
        }

        return found ? categories.toString() : null;
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

    // ✅ CORRIGÉ : Utilisation correcte de la relation avec expenseNoteId
    private String getNoteDetails(Long noteId, String userId, String userRole) {
        log.info("🔍 Recherche de la note #{} pour l'utilisateur {}", noteId, userId);

        Optional<ExpenseNote> noteOpt = expenseNoteRepository.findById(noteId);

        if (noteOpt.isEmpty()) {
            log.warn("❌ Note #{} non trouvée dans la base", noteId);
            return "";
        }

        ExpenseNote note = noteOpt.get();

        // Récupérer les lignes associées à cette note via expenseNoteId
        List<ExpenseLine> lines = note.getLines(); // Utilise la relation définie dans ExpenseNote
        int lineCount = lines != null ? lines.size() : 0;

        log.info("✅ Note #{} trouvée: statut={}, {} lignes",
                noteId, note.getStatus(), lineCount);

        boolean hasAccess = note.getEmployeeId().equals(userId) ||
                "MANAGER".equalsIgnoreCase(userRole) ||
                "ADMIN".equalsIgnoreCase(userRole);

        if (!hasAccess) {
            log.warn("⛔ Accès refusé pour l'utilisateur {} à la note #{}", userId, noteId);
            return "";
        }

        StringBuilder details = new StringBuilder();
        details.append(String.format("\n📝 **DÉTAILS DE LA NOTE #%d**\n", note.getId()));
        details.append(String.format("• 💰 Montant total: %.2f TND\n", note.getTotalAmount()));
        details.append(String.format("• 📊 Statut: %s %s\n", note.getStatus(), note.getStatusEmoji()));
        details.append(String.format("• 📅 Créée le: %s\n", note.getFormattedDate()));

        // ✅ Afficher TOUTES les lignes avec TOUS les détails
        if (lines != null && !lines.isEmpty()) {
            details.append("\n📋 **LIGNES DE LA NOTE:**\n");
            int lineNumber = 1;
            for (ExpenseLine line : lines) {
                details.append(String.format("  • Ligne %d: %s\n", lineNumber++,
                        formatExpenseLineWithFullDetails(line)));
            }
        }

        return details.toString();
    }

    private String formatExpenseLineWithFullDetails(ExpenseLine line) {
        StringBuilder sb = new StringBuilder();

        String categoryName = getCategoryNameById(line.getCategoryId());
        sb.append(String.format("[%s] ", categoryName));
        sb.append(String.format("%.2f TND", line.getAmount()));

        if (line.getDescription() != null) {
            sb.append(String.format(" - Description: %s", line.getDescription()));
        }

        // Ajouter TOUS les champs spécifiques
        List<String> details = new ArrayList<>();
        if (line.getNombrePersonnes() != null)
            details.add(line.getNombrePersonnes() + " personnes");
        if (line.getNombreNuits() != null)
            details.add(line.getNombreNuits() + " nuits");
        if (line.getRepasType() != null)
            details.add("Repas: " + line.getRepasType());
        if (line.getDepart() != null && line.getDestination() != null)
            details.add(line.getDepart() + " → " + line.getDestination());

        if (!details.isEmpty()) {
            sb.append(" (").append(String.join(", ", details)).append(")");
        }

        if (line.getJustificatifPath() != null) {
            sb.append(" 📎");
        }

        return sb.toString();
    }
    // Helper pour formater une ligne d'expense
    private String formatExpenseLine(ExpenseLine line, String categoryName) {
        StringBuilder lineInfo = new StringBuilder();

        // Description ou info spécifique à la catégorie
        String description = line.getDescription() != null ? line.getDescription() : "";

        // Ajouter les infos spécifiques selon le type
        String specificInfo = line.getCategorySpecificInfo();
        if (!specificInfo.equals(description)) {
            description = specificInfo;
        }

        lineInfo.append(String.format("%s - Catégorie: %s - Montant: %.2f TND",
                description, categoryName, line.getAmount()));

        // Ajouter la date si présente
        if (line.getExpenseDate() != null) {
            lineInfo.append(String.format(" (Date: %s)", line.getFormattedDate()));
        }

        // Ajouter le justificatif si présent
        if (line.getJustificatifPath() != null && !line.getJustificatifPath().isEmpty()) {
            lineInfo.append(" 📎");
        }

        return lineInfo.toString();
    }

    // Helper pour obtenir le nom de la catégorie (à implémenter selon votre structure)
    private String getCategoryNameById(Long categoryId) {
        if (categoryId == null) return "Catégorie inconnue";

        // Vous pouvez implémenter un cache ou une requête
        Optional<Category> category = categoryRepository.findById(categoryId);
        return category.map(Category::getName).orElse("Catégorie " + categoryId);
    }

    private ChatResponse buildErrorResponse(ChatRequest request, long duration) {
        return ChatResponse.builder()
                .answer("❌ Désolé, j'ai eu un problème technique. Veuillez réessayer.")
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .responseType("error")
                .processingTimeMs(duration)
                .build();
    }

    private String buildSystemPrompt(String databaseContext, String userContext, String noteDetails, String vectorContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Tu es l'assistant virtuel de Coral.io, une application de gestion de notes de frais en Tunisie.\n\n");
        prompt.append("Tu as accès à deux types d'informations :\n");
        prompt.append("- Les règles de l'entreprise (plafonds, justificatifs, délais)\n");
        prompt.append("- Les données des notes de frais des employés\n\n");

        prompt.append("=== INSTRUCTIONS IMPORTANTES ===\n");
        prompt.append("1️⃣ Réponds de façon **naturelle et humaine**, comme un collègue\n");
        prompt.append("2️⃣ Ne mentionne JAMAIS les sources techniques (pas de 'source 1', 'règles officielles', etc.)\n");
        prompt.append("3️⃣ Si on te demande une règle, donne l'information directement\n");
        prompt.append("4️⃣ Si on te demande une note, donne les détails directement\n");
        prompt.append("5️⃣ Si tu n'as pas l'information, dis simplement 'Je ne peux pas vous répondre' ou 'Je n'ai pas cette information'\n\n");

        prompt.append("=== EXEMPLES DE BONNES RÉPONSES ===\n");
        prompt.append("❌ À ÉVITER: 'D'après la SOURCE 2, votre dernière note est #92'\n");
        prompt.append("✅ À FAIRE: 'Votre dernière note est la #92'\n\n");

        prompt.append("❌ À ÉVITER: 'Selon les règles officielles, le plafond est 200 TND'\n");
        prompt.append("✅ À FAIRE: 'Le plafond pour la restauration est de 200 TND'\n\n");

        prompt.append("❌ À ÉVITER: 'Je ne trouve pas cette information dans les règles officielles'\n");
        prompt.append("✅ À FAIRE: 'Je n'ai pas cette information'\n\n");

        prompt.append("=== INFORMATIONS DISPONIBLES ===\n\n");
        prompt.append(databaseContext).append("\n\n");
        prompt.append(userContext).append("\n\n");

        if (noteDetails != null && !noteDetails.isEmpty()) {
            prompt.append(noteDetails).append("\n\n");
        }

        prompt.append(vectorContext).append("\n\n");

        return prompt.toString();
    }
    private String getDatabaseContext() {
        StringBuilder context = new StringBuilder();
        List<Category> categories = categoryRepository.findByActiveTrue();

        // 1️⃣ RÈGLES STATIQUES (avec un titre TRÈS visible)
        context.append("╔════════════════════════════════════════════════════════════╗\n");
        context.append("║     SECTION 1: RÈGLES STATIQUES OFFICIELLES               ║\n");
        context.append("║     (À UTILISER POUR TOUTES LES QUESTIONS SUR LES RÈGLES) ║\n");
        context.append("╚════════════════════════════════════════════════════════════╝\n");
        context.append(staticRulesService.getStaticRulesOnly());
        context.append("\n");

        // 2️⃣ CATÉGORIES DYNAMIQUES
        context.append("╔════════════════════════════════════════════════════════════╗\n");
        context.append("║     SECTION 2: CATÉGORIES ET PLAFONDS                     ║\n");
        context.append("║     (DONNÉES DYNAMIQUES DE LA BASE)                       ║\n");
        context.append("╚════════════════════════════════════════════════════════════╝\n");

        context.append("📋 Catégories disponibles:\n");
        for (Category cat : categories) {
            context.append(String.format("• %s: plafond %.2f TND\n", cat.getName(), cat.getPlafond()));
        }

        Double avgPlafond = categoryRepository.getAveragePlafond();
        Double maxPlafond = categoryRepository.getMaxPlafond();

        context.append("\n📊 Statistiques:\n");
        context.append(String.format("• Plafond moyen: %.2f TND\n", avgPlafond != null ? avgPlafond : 0));
        context.append(String.format("• Plafond maximum: %.2f TND\n", maxPlafond != null ? maxPlafond : 0));

        return context.toString();
    }
    private String getUserContext(String userId) {
        StringBuilder context = new StringBuilder();
        List<ExpenseNote> recentNotes = expenseNoteRepository.findRecentByEmployee(userId);

        if (recentNotes.isEmpty()) {
            context.append("L'utilisateur n'a pas encore de notes de frais.\n");
        } else {
            context.append("📝 Notes récentes de l'utilisateur:\n");
            for (ExpenseNote note : recentNotes.stream().limit(5).toList()) {
                int lineCount = note.getLines() != null ? note.getLines().size() : 0;
                context.append(String.format("• #%d: %.2f TND %s (%s) - Statut: %s (%d lignes)\n",
                        note.getId(),
                        note.getTotalAmount(),
                        note.getStatusEmoji(),
                        note.getFormattedDate(),
                        note.getStatus(),
                        lineCount));
            }

            Map<String, Long> statusCount = recentNotes.stream()
                    .collect(Collectors.groupingBy(
                            note -> note.getStatus() != null ? note.getStatus() : "INCONNU",
                            Collectors.counting()
                    ));

            context.append("\n📊 Résumé des statuts:\n");
            statusCount.forEach((status, count) ->
                    context.append(String.format("  • %s: %d note(s)\n", status, count)));

            Double totalReimbursed = expenseNoteRepository.getTotalReimbursedForEmployee(userId);
            if (totalReimbursed != null) {
                context.append(String.format("💰 Total remboursé: %.2f TND\n", totalReimbursed));
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
}