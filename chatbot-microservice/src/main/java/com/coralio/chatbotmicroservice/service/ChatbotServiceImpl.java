package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.config.RulesConfig;
import com.coralio.chatbotmicroservice.dto.*;
import com.coralio.chatbotmicroservice.entity.*;
import com.coralio.chatbotmicroservice.model.*;
import com.coralio.chatbotmicroservice.model.Currency;
import com.coralio.chatbotmicroservice.repository.CategoryRepository;
import com.coralio.chatbotmicroservice.repository.ExpenseNoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    @Autowired
    private RulesConfig rulesConfig;

    @Autowired
    private ExpenseApiService expenseApiService;
    @Autowired
    private ExpenseNoteRepository expenseNoteRepository;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ResponseFormatter responseFormatter;
    @Autowired
    private ChatSessionService sessionService;
    @Value("${gateway.service.url:http://localhost:8888}")
    private String gatewayUrl;

    // Intent patterns
    private static final Map<String, Pattern> INTENT_PATTERNS = Map.ofEntries(
            Map.entry("CREATE_NOTE", Pattern.compile("(?i).*(cr[ée]er|nouvelle|ajouter|soumettre|create|new|add|submit).*(note|d[ée]pense|frais|expense).*")),
            Map.entry("VIEW_NOTES", Pattern.compile("(?i).*(voir|afficher|lister|mes|toutes|view|show|list|my|all).*(note|d[ée]pense|frais|expense).*")),
            Map.entry("NOTE_STATUS", Pattern.compile("(?i).*(statut|status|o[ùu] en est|progress|avancement).*(note|d[ée]pense).*")),
            Map.entry("UPLOAD_FILE", Pattern.compile("(?i).*(t[ée]l[ée]charger|upload|joindre|attacher|ajouter).*(fichier|justificatif|pdf|facture|reçu|file|receipt|invoice).*")),
            Map.entry("VALIDATION_RULES", Pattern.compile("(?i).*(r[èe]gle|condition|validation|autoriser|permis|rule|allowed|permitted).*(d[ée]pense|frais).*")),
            Map.entry("HELP", Pattern.compile("(?i).*(aide|help|comment|how to|assistance|support).*")),
            Map.entry("DELETE_NOTE", Pattern.compile("(?i).*(supprimer|effacer|annuler|delete|remove|cancel).*(note|d[ée]pense).*")),
            Map.entry("TOTAL_AMOUNT", Pattern.compile("(?i).*(total|somme|montant).*(d[ée]pense|frais).*")),
            Map.entry("FILTER_BY_DATE", Pattern.compile("(?i).*(entre|entre|date|p[ée]riode|mois|ann[ée]e|between|period|month|year).*(d[ée]pense).*")),
            Map.entry("MANAGER_ACTIONS", Pattern.compile("(?i).*(valider|approuver|refuser|rejeter|commenter|approve|reject|comment).*(note|d[ée]pense).*")),
            Map.entry("ADMIN_ACTIONS", Pattern.compile("(?i).*(rembourser|reimburser|paiement|payment).*(note|d[ée]pense).*")),
            Map.entry("FILE_INFO", Pattern.compile("(?i).*(fichier|justificatif|document).*(info|information|détail|détails|detail).*")),
            Map.entry("CATEGORY_PLAFOND", Pattern.compile("(?i).*(plafond|montant max|maximum|limite|limit).*(cat[ée]gorie|type|category).*")),
            Map.entry("SPECIAL_RULES", Pattern.compile("(?i).*(r[èe]gle sp[ée]ciale|exception|cas particulier|special).*")),
            Map.entry("ALERT_RULES", Pattern.compile("(?i).*(alerte|warning|attention).*")),
            Map.entry("WORKFLOW", Pattern.compile("(?i).*(workflow|processus|[ée]tapes|validation|circuit).*")),
            Map.entry("FAQ", Pattern.compile("(?i).*(question|faq|frequently asked).*")),
            Map.entry("HOLIDAY_RULES", Pattern.compile("(?i).*(jour f[ée]ri[ée]|week-end|weekend|samedi|dimanche|holiday).*")),
            Map.entry("CURRENCY", Pattern.compile("(?i).*(devise|currency|conversion|tnd|euro|dollar).*"))
    );

    @Autowired
    @Lazy  // ✅ AJOUTER CETTE ANNOTATION
    private SmartChatbotService smartChatbotService;
    @Override
    public ChatResponse processQuestion(ChatRequest request) {
        log.info("Processing question from user {}: {}", request.getUserId(), request.getQuestion());

        // ✅ Récupérer l'historique de la session
        String sessionToken = request.getSessionId();
        List<ChatMessage> history = getConversationHistory(sessionToken);

        log.info("🔍 DEBUG - Historique récupéré: {} messages", history.size());
        for (ChatMessage m : history) {
            log.info("  -> {}: {}", m.isUser() ? "User" : "Bot", m.getContent());
        }

        // ✅ Extraire le contexte (dernière note mentionnée)
        Long lastMentionedNoteId = extractLastNoteIdFromHistory(history);
        log.info("🔍 DEBUG - lastMentionedNoteId: {}", lastMentionedNoteId);

        // ✅ Vérifier si "cette note" fait référence à une note précédente
        String question = request.getQuestion();
        String lowerQuestion = question.toLowerCase();
        boolean refersToPreviousNote = lowerQuestion.contains("cette note") ||
                (lowerQuestion.contains("cette") && !lowerQuestion.contains("cette note")) ||
                (lowerQuestion.contains("la note") && !lowerQuestion.matches(".*\\d+.*"));

        log.info("🔍 DEBUG - refersToPreviousNote: {}, question: '{}'", refersToPreviousNote, question);

        Long effectiveNoteId = null;

        // Si la question fait référence à une note précédente, utiliser le contexte
        if (refersToPreviousNote && lastMentionedNoteId != null) {
            effectiveNoteId = lastMentionedNoteId;
            log.info("🔍 Contexte détecté dans SIMPLE CHATBOT: 'cette note' fait référence à la note #{}", lastMentionedNoteId);
        } else {
            // Sinon, extraire normalement
            effectiveNoteId = extractNoteId(question);
        }

        // Si toujours pas de note ID, chercher dans l'historique
        if (effectiveNoteId == null && lastMentionedNoteId != null) {
            if (lowerQuestion.contains("cette") && !lowerQuestion.contains("note")) {
                effectiveNoteId = lastMentionedNoteId;
                log.info("🔍 Contexte détecté dans SIMPLE CHATBOT: 'cette' fait référence à la note #{}", lastMentionedNoteId);
            }
        }

        log.info("📝 Note ID final dans SIMPLE CHATBOT: {}", effectiveNoteId);

        // ✅ Passer le rôle à detectIntent avec le contexte
        String intent = detectIntentWithContext(question, request.getUserRole(), effectiveNoteId);
        log.info("Detected intent: {}", intent);

        return processIntent(intent, request, effectiveNoteId);
    }
    /**
     * Détecte l'intent en tenant compte du contexte de note
     */
    private String detectIntentWithContext(String question, String userRole, Long contextNoteId) {
        String lowerQuestion = question.toLowerCase();

        // ✅ FORCER la détection si on a un contexte ET la question parle de "cette note"
        if (contextNoteId != null && (lowerQuestion.contains("cette note") || lowerQuestion.contains("cette"))) {
            // Pour l'analyse des règles
            if (lowerQuestion.contains("respecte") ||
                    lowerQuestion.contains("règle") ||
                    lowerQuestion.contains("règles") ||
                    lowerQuestion.contains("conformité") ||
                    lowerQuestion.contains("valide")) {
                log.info("📊 FORCAGE: Intent ANALYSE_NOTE avec contexte: note #{}", contextNoteId);
                return "ANALYSE_NOTE";
            }
            // Pour le statut
            if (lowerQuestion.contains("statut") ||
                    lowerQuestion.contains("status") ||
                    lowerQuestion.contains("où en est")) {
                log.info("📊 FORCAGE: Intent NOTE_STATUS avec contexte: note #{}", contextNoteId);
                return "NOTE_STATUS";
            }
            // Pour les détails (si l'utilisateur dit "cette note" seul)
            if (lowerQuestion.contains("détail") ||
                    lowerQuestion.contains("details") ||
                    lowerQuestion.equals("cette note")) {
                log.info("📊 FORCAGE: Intent NOTE_DETAILS avec contexte: note #{}", contextNoteId);
                return "NOTE_DETAILS";
            }
        }

        // ✅ Si on a un contexte de note (mais pas de "cette note")
        if (contextNoteId != null) {
            if (lowerQuestion.contains("respecte") ||
                    lowerQuestion.contains("règle") ||
                    lowerQuestion.contains("conformité") ||
                    lowerQuestion.contains("valide")) {
                log.info("📊 Intent ANALYSE_NOTE avec contexte: note #{}", contextNoteId);
                return "ANALYSE_NOTE";
            }
            if (lowerQuestion.contains("statut") ||
                    lowerQuestion.contains("où en est") ||
                    lowerQuestion.contains("status")) {
                log.info("📊 Intent NOTE_STATUS avec contexte: note #{}", contextNoteId);
                return "NOTE_STATUS";
            }
        }

        // Sinon, utiliser la détection normale
        return detectIntent(question, userRole);
    }
    private String detectIntent(String question, String userRole) {
        String lowerQuestion = question.toLowerCase();
        // ✅ PRIORITÉ 0: Détection pour les notes filtrées par statut (MUST COME FIRST!)
        String[] statusKeywords = {"en attente", "validées", "validée", "refusées", "refusée", "remboursées", "remboursée"};
        String detectedStatus = null;

        for (String keyword : statusKeywords) {
            if (lowerQuestion.contains("mes notes " + keyword) ||
                    lowerQuestion.contains("notes " + keyword) ||
                    (lowerQuestion.contains(keyword) && lowerQuestion.contains("notes"))) {
                detectedStatus = keyword;
                break;
            }
        }

        if (detectedStatus != null) {
            // Map French status to enum
            String status = switch (detectedStatus) {
                case "en attente" -> "EN_ATTENTE";
                case "validées", "validée" -> "VALIDEE";
                case "refusées", "refusée" -> "REFUSEE";
                case "remboursées", "remboursée" -> "REMBOURSEE";
                default -> null;
            };

            if (status != null) {
                log.info("📊 Intent FILTER_NOTES_BY_STATUS détecté pour {}: {}",
                        "MANAGER".equalsIgnoreCase(userRole) ? "manager" : "employé", status);
                return "FILTER_NOTES_BY_STATUS";
            }
        }
        // ✅ PRIORITÉ 0.1: Détection pour les questions simples de note (sans "détail")
        // Exemples: "donner la note 92", "afficher note 92", "note 92", "donner note 92"
        if ((lowerQuestion.contains("donner") || lowerQuestion.contains("afficher") ||
                lowerQuestion.contains("voir") || lowerQuestion.contains("lister") ||
                lowerQuestion.equals("note") || lowerQuestion.matches("note\\s+\\d+")) &&
                lowerQuestion.matches(".*\\d+.*") &&
                !lowerQuestion.contains("statut") && !lowerQuestion.contains("status")) {
            log.info("📊 Intent NOTE_DETAILS détecté pour une note spécifique (commande simple)");
            return "NOTE_DETAILS";
        }

        // ✅ PRIORITÉ 1: Détection des questions sur les détails d'une note spécifique
        if ((lowerQuestion.contains("détail") || lowerQuestion.contains("details") ||
                lowerQuestion.contains("afficher") || lowerQuestion.contains("lister") ||
                lowerQuestion.contains("donner") || lowerQuestion.contains("info")) &&
                lowerQuestion.contains("note") &&
                lowerQuestion.matches(".*\\d+.*")) {
            log.info("📊 Intent NOTE_DETAILS détecté pour une note spécifique");
            return "NOTE_DETAILS";
        }

        // ✅ PRIORITÉ 2: Détection pour "mes notes" (SANS numéro)
        if (lowerQuestion.contains("mes notes") && !lowerQuestion.matches(".*\\d+.*")) {
            if ("MANAGER".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
                log.info("📊 Intent MANAGER_ACTIONS détecté: mes notes (manager) → toutes les notes du département");
                return "MANAGER_ACTIONS";
            } else {
                log.info("📊 Intent VIEW_NOTES détecté: mes notes (employé)");
                return "VIEW_NOTES";
            }
        }
        // ✅ PRIORITÉ 3: Détection pour "toutes les notes du département"
        if (lowerQuestion.contains("toutes les notes") ||
                lowerQuestion.contains("notes du département")) {
            log.info("📊 Intent MANAGER_ACTIONS détecté: toutes les notes du département");
            return "MANAGER_ACTIONS";
        }

        // ✅ PRIORITÉ 4: Détection pour "notes en attente" (manager)
        if (lowerQuestion.contains("notes en attente") ||
                lowerQuestion.contains("en attente de validation") ||
                lowerQuestion.contains("notes à valider")) {
            log.info("📊 Intent MANAGER_ACTIONS détecté: notes en attente");
            return "MANAGER_ACTIONS";
        }

        // ✅ BUG 1: Détection pour "plafond" seul
        if (lowerQuestion.equals("plafond") || lowerQuestion.equals("plafonds")) {
            log.info("📊 Intent CATEGORY_PLAFOND_ALL détecté (tous les plafonds)");
            return "CATEGORY_PLAFOND_ALL";
        }

        // ✅ BUG 2: Détection pour les questions de vérification/conformité (COMPLEX)
        if ((lowerQuestion.contains("vérifie") || lowerQuestion.contains("verifie") ||
                lowerQuestion.contains("respecte") || lowerQuestion.contains("conformité")) &&
                lowerQuestion.contains("note") && lowerQuestion.matches(".*\\d+.*")) {
            log.info("📊 Intent COMPLEXE - ANALYSE_NOTE détecté");
            return "ANALYSE_NOTE";
        }

        // ✅ BUG 3: Détection pour les questions de comparaison (COMPLEX)
        if (lowerQuestion.contains("compare") || lowerQuestion.contains("comparer") ||
                lowerQuestion.contains("différence") || lowerQuestion.contains("versus") ||
                lowerQuestion.contains("vs") || (lowerQuestion.contains("janvier") && lowerQuestion.contains("février")) ||
                (lowerQuestion.contains("mois") && lowerQuestion.contains("comparer"))) {
            log.info("📊 Intent COMPLEXE - COMPARAISON_NOTES détecté");
            return "COMPARAISON_NOTES";
        }

        // ✅ PRIORITÉ 5: Détection spécifique pour "plafond restauration" et autres catégories
        if ((lowerQuestion.contains("plafond") || lowerQuestion.contains("montant max") || lowerQuestion.contains("limite")) &&
                (lowerQuestion.contains("restauration") ||
                        lowerQuestion.contains("hébergement") ||
                        lowerQuestion.contains("hebergement") ||
                        lowerQuestion.contains("transport") ||
                        lowerQuestion.contains("carburant") ||
                        lowerQuestion.contains("repas") ||
                        lowerQuestion.contains("hôtel") ||
                        lowerQuestion.contains("hotel"))) {
            log.info("📊 Intent CATEGORY_PLAFOND spécifique détecté");
            return "CATEGORY_PLAFOND";
        }

        // ✅ PRIORITÉ 6: Détection spécifique pour "devise" et "conversion"
        if (lowerQuestion.contains("devise") || lowerQuestion.contains("conversion") ||
                lowerQuestion.contains("euro") || lowerQuestion.contains("dollar") ||
                lowerQuestion.contains("usd") || lowerQuestion.contains("eur")) {
            log.info("📊 Intent CURRENCY détecté");
            return "CURRENCY";
        }

        // ✅ PRIORITÉ 7: Détection pour le statut d'une note
        if ((lowerQuestion.contains("statut") || lowerQuestion.contains("status") ||
                lowerQuestion.contains("où en est") || lowerQuestion.contains("avancement")) &&
                (lowerQuestion.contains("note") || lowerQuestion.matches(".*#?\\d+.*"))) {
            log.info("📊 Intent NOTE_STATUS détecté");
            return "NOTE_STATUS";
        }

        // ✅ PRIORITÉ 8: Détection pour le total des dépenses
        if (lowerQuestion.contains("total") &&
                (lowerQuestion.contains("dépense") || lowerQuestion.contains("frais") ||
                        lowerQuestion.contains("remboursement"))) {
            log.info("📊 Intent TOTAL_AMOUNT détecté");
            return "TOTAL_AMOUNT";
        }

        // ✅ PRIORITÉ 9: Détection spécifique pour les justificatifs
        if (lowerQuestion.contains("justificatif") || lowerQuestion.contains("format") ||
                lowerQuestion.contains("taille") || lowerQuestion.contains("pdf") ||
                lowerQuestion.contains("jpg") || lowerQuestion.contains("png")) {
            log.info("📊 Intent VALIDATION_RULES détecté (justificatifs)");
            return "VALIDATION_RULES";
        }

        // ✅ PRIORITÉ 10: Détection spécifique pour le workflow
        if (lowerQuestion.contains("workflow") || lowerQuestion.contains("validation") ||
                lowerQuestion.contains("étapes") || lowerQuestion.contains("processus")) {
            log.info("📊 Intent WORKFLOW détecté");
            return "WORKFLOW";
        }

        // ✅ PRIORITÉ 11: Détection spécifique pour les alertes
        if (lowerQuestion.contains("alerte") || lowerQuestion.contains("alertes") ||
                lowerQuestion.contains("facture en double") ||
                lowerQuestion.contains("dépassement plafond") ||
                lowerQuestion.contains("justificatif illisible") ||
                lowerQuestion.contains("date incohérente")) {
            log.info("📊 Intent ALERT_RULES détecté");
            return "ALERT_RULES";
        }

        // ✅ PRIORITÉ 12: Détection spécifique pour les jours fériés
        if (lowerQuestion.contains("jour férié") || lowerQuestion.contains("jours fériés") ||
                lowerQuestion.contains("ferié") || lowerQuestion.contains("week-end") ||
                lowerQuestion.contains("samedi") || lowerQuestion.contains("dimanche")) {
            log.info("📊 Intent HOLIDAY_RULES détecté");
            return "HOLIDAY_RULES";
        }

        // ✅ PRIORITÉ 13: Détection spécifique pour la FAQ
        if (lowerQuestion.contains("faq") || lowerQuestion.contains("question fréquente") ||
                (lowerQuestion.contains("remboursement") && lowerQuestion.contains("partiel"))) {
            log.info("📊 Intent FAQ détecté");
            return "FAQ";
        }

        // ✅ PRIORITÉ 14: Détection spécifique pour les règles de remboursement
        if ((lowerQuestion.contains("règle") || lowerQuestion.contains("règles") ||
                lowerQuestion.contains("regle") || lowerQuestion.contains("regles")) &&
                lowerQuestion.contains("remboursement")) {
            log.info("📊 Intent VALIDATION_RULES détecté (règles de remboursement)");
            return "VALIDATION_RULES";
        }

        // ✅ PRIORITÉ 15: Vérifier les patterns simples
        for (Map.Entry<String, Pattern> entry : IntentClassifierService.SIMPLE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).matches()) {
                log.info("✅ Intent SIMPLE détecté: {}", entry.getKey());
                return entry.getKey();
            }
        }

        // ✅ PRIORITÉ 16: Vérifier les patterns complexes
        for (Map.Entry<String, Pattern> entry : IntentClassifierService.COMPLEX_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).matches()) {
                log.info("✅ Intent COMPLEXE détecté: {}", entry.getKey());
                return entry.getKey();
            }
        }

        // ✅ PRIORITÉ 17: Analyse par mots-clés pour les plafonds génériques
        if (lowerQuestion.contains("plafond") || lowerQuestion.contains("montant")) {
            log.info("📊 Intent CATEGORY_PLAFOND via mots-clés (générique)");
            return "CATEGORY_PLAFOND";
        }

        // ✅ PRIORITÉ 18: Analyse par mots-clés pour la création de note
        if (lowerQuestion.contains("créer") || lowerQuestion.contains("nouvelle") ||
                lowerQuestion.contains("ajouter")) {
            log.info("📊 Intent CREATE_NOTE via mots-clés");
            return "CREATE_NOTE";
        }

        // ✅ PRIORITÉ 19: Analyse par mots-clés pour le téléchargement
        if (lowerQuestion.contains("télécharger") || lowerQuestion.contains("upload") ||
                lowerQuestion.contains("joindre")) {
            log.info("📊 Intent UPLOAD_FILE via mots-clés");
            return "UPLOAD_FILE";
        }

        // ✅ PRIORITÉ 20: Analyse par mots-clés pour la suppression
        if (lowerQuestion.contains("supprimer") || lowerQuestion.contains("effacer") ||
                lowerQuestion.contains("annuler")) {
            log.info("📊 Intent DELETE_NOTE via mots-clés");
            return "DELETE_NOTE";
        }

        // ✅ PRIORITÉ 21: Fallback vers HELP
        log.info("❌ Aucun intent détecté, fallback vers HELP");
        return "HELP";
    }

    private ChatResponse processIntent(String intent, ChatRequest request, Long contextNoteId) {
        // Si l'intention est HELP et que c'est le fallback, on utilise notre nouvelle méthode
        if ("HELP".equals(intent) && !request.getQuestion().toLowerCase().contains("aide")) {
            return handleUnknownIntent(request);
        }
        if (Set.of("GREETING", "GRATITUDE", "FAREWELL", "POSITIVE_FEEDBACK").contains(intent)) {
            return handleSocialIntent(intent, request);
        }

        return switch (intent) {
            case "CREATE_NOTE" -> handleCreateNote(request);
            case "VIEW_NOTES" -> handleViewNotes(request);
            case "FILTER_NOTES_BY_STATUS" -> {
                // Extract the status from the question
                String question = request.getQuestion().toLowerCase();
                String status = null;

                if (question.contains("en attente")) {
                    status = "EN_ATTENTE";
                } else if (question.contains("validée") || question.contains("validées")) {
                    status = "VALIDEE";
                } else if (question.contains("refusée") || question.contains("refusées")) {
                    status = "REFUSEE";
                } else if (question.contains("remboursée") || question.contains("remboursées")) {
                    status = "REMBOURSEE";
                }

                yield handleFilterNotesByStatus(request, status);
            }
            case "NOTE_DETAILS" -> handleNoteDetails(request, contextNoteId);
            case "NOTE_STATUS" -> handleNoteStatus(request, contextNoteId);
            case "UPLOAD_FILE" -> handleUploadFile(request);
            case "VALIDATION_RULES" -> handleValidationRules(request);
            case "DELETE_NOTE" -> handleDeleteNote(request);
            case "TOTAL_AMOUNT" -> handleTotalAmount(request);
            case "FILTER_BY_DATE" -> handleFilterByDate(request);
            case "MANAGER_ACTIONS" -> handleManagerActions(request);
            case "ADMIN_ACTIONS" -> handleAdminActions(request);
            case "FILE_INFO" -> handleFileInfo(request);
            case "CATEGORY_PLAFOND" -> handleCategoryPlafond(request);
            case "CATEGORY_PLAFOND_ALL" -> handleAllPlafonds(request);

            // ✅ MODIFICATION ICI : Passer le contexte au Smart Chatbot
            case "ANALYSE_NOTE" -> {
                // Si on a un contexte, l'ajouter à la requête
                if (contextNoteId != null) {
                    log.info("📤 Transmission du contexte note #{} au Smart Chatbot", contextNoteId);
                    // ✅ Créer une nouvelle map mutable (HashMap)
                    Map<String, Object> context = new HashMap<>();

                    // Copier l'ancien contexte s'il existe
                    if (request.getContext() != null) {
                        context.putAll(request.getContext());
                    }

                    // Ajouter le nouveau contexte
                    context.put("contextNoteId", contextNoteId);
                    request.setContext(context);
                }
                yield smartChatbotService.processSmartQuestion(request);
            }
            case "COMPARAISON_NOTES" -> smartChatbotService.processSmartQuestion(request);
            case "SPECIAL_RULES" -> handleSpecialRules(request);
            case "ALERT_RULES" -> handleAlertRules(request);
            case "WORKFLOW" -> handleWorkflow(request);
            case "FAQ" -> handleFAQ(request);
            case "HOLIDAY_RULES" -> handleHolidayRules(request);
            case "CURRENCY" -> handleCurrency(request);
            default -> handleUnknownIntent(request);
        };
    }

    // ==================== MÉTHODES POUR LE MANAGER ====================

    /**
     * Extrait le département du token JWT
     */
    private Long extractDepartmentFromToken() {
        try {
            String authToken = extractAuthToken();
            if (authToken == null || authToken.isEmpty()) {
                log.warn("⚠️ Token manquant");
                return null;
            }

            String token = authToken.startsWith("Bearer ") ? authToken.substring(7) : authToken;
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(payload);

            JsonNode deptNode = root.get("departmentId");
            if (deptNode == null) return null;

            Long departmentId = deptNode.isNumber() ? deptNode.asLong() : Long.parseLong(deptNode.asText());
            log.info("✅ DepartmentId extrait du token: {}", departmentId);
            return departmentId;

        } catch (Exception e) {
            log.error("❌ Erreur extraction département: {}", e.getMessage());
            return null;
        }
    }

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

    /**
     * Récupère les projets d'un département
     */
    private List<ProjectInfo> getProjectsByDepartment(Long departmentId) {
        try {
            String url = getProjectServiceUrl() + "/public/by-department/" + departmentId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + extractAuthToken());
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<ProjectInfo[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ProjectInfo[].class
            );

            ProjectInfo[] projects = response.getBody();
            return projects != null ? Arrays.asList(projects) : Collections.emptyList();

        } catch (Exception e) {
            log.error("Erreur récupération projets du département {}: {}", departmentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Récupère le nom d'un projet
     */
    private String getProjectNameById(Long projectId, List<ProjectInfo> projects) {
        return projects.stream()
                .filter(p -> p.getId().equals(projectId))
                .findFirst()
                .map(ProjectInfo::getName)
                .orElse("Projet " + projectId);
    }

    /**
     * Retourne l'emoji correspondant au statut d'une note
     */
    private String getStatusEmojiForNote(String status) {
        if (status == null) return "📝";
        return switch (status.toUpperCase()) {
            case "EN_ATTENTE" -> "⏳";
            case "VALIDEE" -> "✅";
            case "REFUSEE" -> "❌";
            case "REMBOURSEE" -> "💰";
            default -> "📝";
        };
    }

    /**
     * Formate une date pour l'affichage
     */
    private String formatDate(LocalDateTime date) {
        if (date == null) return "Non spécifiée";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return date.format(formatter);
    }

    /**
     * Formate l'ID de l'employé pour l'affichage
     */
    private String formatEmployeeId(String employeeId) {
        if (employeeId == null) return "Inconnu";
        if (employeeId.length() <= 8) return employeeId;
        return employeeId.substring(0, 5) + "...";
    }

    /**
     * Affiche les notes en attente pour un manager (filtrées par département)
     */
    private ChatResponse showPendingNotesForManager(ChatRequest request) {
        String managerId = request.getUserId();

        log.info("📋 Récupération des notes en attente pour le manager {}", managerId);

        try {
            // 1. Vérifier d'abord si le token est présent
            String authToken = extractAuthToken();
            if (authToken == null || authToken.isEmpty()) {
                log.error("🔒 Token manquant pour le manager {}", managerId);
                return ChatResponse.builder()
                        .answer("🔒 Vous n'êtes pas authentifié. Veuillez vous reconnecter.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .responseType("error")
                        .build();
            }

            // 2. Récupérer le département du manager depuis le token
            Long managerDepartmentId = extractDepartmentFromToken();

            if (managerDepartmentId == null) {
                return ChatResponse.builder()
                        .answer("❌ Impossible de déterminer votre département.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            log.info("🏢 Manager département: {}", managerDepartmentId);

            // 3. Récupérer TOUTES les notes du département
            List<ExpenseNoteDTO> departmentNotes = expenseApiService.getNotesByDepartment(managerDepartmentId);

            log.info("📊 Total notes dans le département {}: {}", managerDepartmentId, departmentNotes.size());

            if (departmentNotes.isEmpty()) {
                return ChatResponse.builder()
                        .answer("📭 Aucune note trouvée dans votre département.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 4. Filtrer les notes en attente
            List<ExpenseNoteDTO> pendingNotes = departmentNotes.stream()
                    .filter(note -> "EN_ATTENTE".equals(note.getStatus()))
                    .collect(Collectors.toList());

            if (pendingNotes.isEmpty()) {
                return ChatResponse.builder()
                        .answer("📭 Aucune note en attente dans votre département.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 5. Récupérer les projets pour avoir les noms
            List<ProjectInfo> departmentProjects = getProjectsByDepartment(managerDepartmentId);

            // 6. Construire la réponse
            StringBuilder sb = new StringBuilder();
            sb.append("⏳ **Notes en attente dans votre département** (" + pendingNotes.size() + ")\n\n");

            pendingNotes.stream().limit(5).forEach(note -> {
                String projectName = getProjectNameById(note.getProjectId(), departmentProjects);
                sb.append(String.format("• #%d - Projet: %s - %.2f TND - Employé: %s\n",
                        note.getId(),
                        projectName != null ? projectName : "Sans projet",
                        note.getTotalAmount(),
                        formatEmployeeId(note.getEmployeeId())));
            });

            if (pendingNotes.size() > 5) {
                sb.append(String.format("\n... et %d autre(s) note(s)", pendingNotes.size() - 5));
            }

            sb.append("\n\n**Pour voir toutes les notes:**");
            sb.append("\n• `toutes les notes`");
            sb.append("\n• `détails de la note 123` (pour une note spécifique)");

            return ChatResponse.builder()
                    .answer(sb.toString())
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .quickReplies(List.of(
                            QuickReply.builder().text("Toutes les notes").payload("ALL_NOTES").icon("📋").build(),
                            QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                    ))
                    .build();

        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();

            // ✅ Gérer spécifiquement l'erreur 401
            if (errorMessage != null && errorMessage.startsWith("UNAUTHORIZED")) {
                String cleanMessage = errorMessage.replace("UNAUTHORIZED:", "").trim();
                log.error("🔒 Erreur d'authentification pour le manager {}: {}", managerId, cleanMessage);
                return ChatResponse.builder()
                        .answer("🔒 " + cleanMessage)
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .responseType("error")
                        .build();
            }

            log.error("Erreur lors de la récupération des notes: {}", e.getMessage());
            return ChatResponse.builder()
                    .answer("❌ Une erreur est survenue. Veuillez réessayer.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
    /**
     * Affiche toutes les notes du département du manager (pas seulement en attente)
     */
    private ChatResponse showAllNotesForManager(ChatRequest request) {
        String managerId = request.getUserId();

        log.info("📋 Récupération de toutes les notes pour le manager {}", managerId);

        try {
            // 1. Récupérer le département du manager depuis le token
            Long managerDepartmentId = extractDepartmentFromToken();

            if (managerDepartmentId == null) {
                return ChatResponse.builder()
                        .answer("❌ Impossible de déterminer votre département.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            log.info("🏢 Manager département: {}", managerDepartmentId);

            // 2. Récupérer TOUTES les notes du département
            List<ExpenseNoteDTO> departmentNotes = expenseApiService.getNotesByDepartment(managerDepartmentId);

            log.info("📊 Total notes dans le département {}: {}", managerDepartmentId, departmentNotes.size());

            if (departmentNotes.isEmpty()) {
                return ChatResponse.builder()
                        .answer("📭 Aucune note trouvée dans votre département.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 3. Récupérer les projets pour avoir les noms
            List<ProjectInfo> departmentProjects = getProjectsByDepartment(managerDepartmentId);

            // 4. Trier par date (plus récentes d'abord)
            List<ExpenseNoteDTO> sortedNotes = departmentNotes.stream()
                    .sorted((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .collect(Collectors.toList());

            // 5. Construire la réponse
            StringBuilder sb = new StringBuilder();
            sb.append("📋 **Toutes les notes dans votre département** (" + sortedNotes.size() + ")\n\n");

            sortedNotes.stream().limit(5).forEach(note -> {
                String projectName = getProjectNameById(note.getProjectId(), departmentProjects);
                String statusEmoji = getStatusEmojiForNote(note.getStatus());
                sb.append(String.format("%s #%d - Projet: %s - %.2f TND - %s - Employé: %s\n",
                        statusEmoji,
                        note.getId(),
                        projectName != null ? projectName : "Sans projet",
                        note.getTotalAmount(),
                        note.getStatus(),
                        formatEmployeeId(note.getEmployeeId())));
            });

            if (sortedNotes.size() > 5) {
                sb.append(String.format("\n... et %d autre(s) note(s)", sortedNotes.size() - 5));
            }

            sb.append("\n\n**Pour voir les notes en attente uniquement:**");
            sb.append("\n• `notes en attente`");
            sb.append("\n• `détails de la note 123` (pour une note spécifique)");

            return ChatResponse.builder()
                    .answer(sb.toString())
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .quickReplies(List.of(
                            QuickReply.builder().text("Notes en attente").payload("PENDING_NOTES").icon("⏳").build(),
                            QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des notes: {}", e.getMessage());
            return ChatResponse.builder()
                    .answer("❌ Une erreur est survenue. Veuillez réessayer.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Affiche les détails d'une note spécifique pour un manager
     */
    private ChatResponse showNoteDetailsForManager(ChatRequest request, Long noteId) {
        String managerId = request.getUserId();

        log.info("🔍 Récupération des détails de la note #{} pour le manager {}", noteId, managerId);

        try {
            // 1. Récupérer le département du manager depuis le token
            Long managerDepartmentId = extractDepartmentFromToken();

            if (managerDepartmentId == null) {
                return ChatResponse.builder()
                        .answer("❌ Impossible de déterminer votre département.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 2. Récupérer les détails de la note
            ExpenseNoteDTO note = expenseApiService.getNoteById(noteId);

            if (note == null) {
                return ChatResponse.builder()
                        .answer("❌ La note #" + noteId + " n'existe pas.")
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 3. ✅ Récupérer les lignes de la note
            List<ExpenseLine> lines = expenseApiService.getExpenseLinesByNoteId(noteId);

            // 4. Récupérer les projets pour avoir le nom
            List<ProjectInfo> departmentProjects = getProjectsByDepartment(managerDepartmentId);
            String projectName = getProjectNameById(note.getProjectId(), departmentProjects);

            // 5. Construire la réponse avec les lignes
            StringBuilder sb = new StringBuilder();
            sb.append("📝 **Détails de la note #" + noteId + "**\n\n");
            sb.append(String.format("• 💰 Montant total: %.2f TND\n", note.getTotalAmount()));
            sb.append(String.format("• 📊 Statut: %s %s\n", note.getStatus(), getStatusEmojiForNote(note.getStatus())));
            sb.append(String.format("• 📅 Créée le: %s\n", formatDate(note.getCreatedAt())));
            sb.append(String.format("• 👤 Employé: %s\n", formatEmployeeId(note.getEmployeeId())));
            sb.append(String.format("• 📁 Projet: %s\n", projectName));

            if (note.getDecisionComment() != null && !note.getDecisionComment().isEmpty()) {
                sb.append(String.format("• 💬 Commentaire: %s\n", note.getDecisionComment()));
            }

            if (note.getDecidedBy() != null) {
                sb.append(String.format("• 👤 Décidé par: %s\n", note.getDecidedBy()));
            }

            if (note.getDecidedAt() != null) {
                sb.append(String.format("• ⏰ Décision le: %s\n", formatDate(note.getDecidedAt())));
            }

            // ✅ Ajouter les lignes de la note
            if (lines != null && !lines.isEmpty()) {
                sb.append("\n📋 **Lignes de la note:**\n");
                int lineNumber = 1;
                for (ExpenseLine line : lines) {
                    String categoryName = getCategoryNameFromRepository(line.getCategoryId());

                    sb.append(String.format("  • Ligne %d: [%s] %.2f TND",
                            lineNumber++,
                            categoryName,
                            line.getAmount()));

                    // Ajouter la description si présente
                    if (line.getDescription() != null && !line.getDescription().isEmpty()) {
                        sb.append(" - " + line.getDescription());
                    }

                    // Ajouter les détails spécifiques
                    List<String> details = new ArrayList<>();
                    if (line.getNombrePersonnes() != null) {
                        details.add(line.getNombrePersonnes() + " personnes");
                    }
                    if (line.getNombreNuits() != null) {
                        details.add(line.getNombreNuits() + " nuits");
                    }
                    if (line.getRepasType() != null) {
                        details.add("Repas: " + line.getRepasType());
                    }
                    if (line.getDepart() != null && line.getDestination() != null) {
                        details.add(line.getDepart() + " → " + line.getDestination());
                    }

                    if (!details.isEmpty()) {
                        sb.append(" (" + String.join(", ", details) + ")");
                    }

                    if (line.getJustificatifPath() != null && !line.getJustificatifPath().isEmpty()) {
                        sb.append(" 📎");
                    }

                    sb.append("\n");
                }
            } else {
                sb.append("\n📋 Aucune ligne de détail pour cette note.\n");
            }

            return ChatResponse.builder()
                    .answer(sb.toString())
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .quickReplies(List.of(
                            QuickReply.builder().text("Notes en attente").payload("PENDING_NOTES").icon("⏳").build(),
                            QuickReply.builder().text("Toutes les notes").payload("ALL_NOTES").icon("📋").build(),
                            QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la note #{}: {}", noteId, e.getMessage());
            return ChatResponse.builder()
                    .answer("❌ Une erreur est survenue. Veuillez réessayer.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Gère les actions des managers - UNIQUEMENT consultation des notes
     */
    private ChatResponse handleManagerActions(ChatRequest request) {
        if (!"MANAGER".equalsIgnoreCase(request.getUserRole()) && !"ADMIN".equalsIgnoreCase(request.getUserRole())) {
            return ChatResponse.builder()
                    .answer("❌ Cette action est réservée aux managers et administrateurs.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        try {
            String question = request.getQuestion().toLowerCase();

            // ✅ Si c'est une question sur les notes en attente
            if (question.contains("notes en attente") ||
                    question.contains("en attente") ||
                    question.contains("en attente de validation") ||
                    question.contains("notes à valider")) {
                return showPendingNotesForManager(request);
            }

            // ✅ Si c'est une question sur toutes les notes
            if (question.contains("toutes les notes") ||
                    question.contains("mes notes") ||
                    question.contains("notes du département") ||
                    question.contains("liste des notes")) {
                return showAllNotesForManager(request);
            }

            // Si c'est une question sur une note spécifique
            Long noteId = extractNoteId(question);
            if (noteId != null) {
                return showNoteDetailsForManager(request, noteId);
            }

            return showPendingNotesForManager(request);

        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("UNAUTHORIZED")) {
                return ChatResponse.builder()
                        .answer("🔒 " + errorMessage.replace("UNAUTHORIZED:", "").trim())
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .responseType("error")
                        .build();
            }
            throw e;
        }
    }

    // ==================== CLASSE INTERNE PROJECTINFO ====================

    public static class ProjectInfo {
        private Long id;
        private String name;
        private String code;
        private Long departmentId;
        private String status;

        // Getters et setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // ==================== MÉTHODES EXISTANTES ====================

    private ChatResponse handleCreateNote(ChatRequest request) {
        List<QuickReply> replies = rulesConfig.getCategories().stream()
                .map(cat -> QuickReply.builder()
                        .text(cat.getName())
                        .payload("CATEGORY_" + cat.getId())
                        .icon(cat.getIcon())
                        .build())
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("📝 **Créer une nouvelle note de frais**\n\n");
        sb.append("Choisissez une catégorie de dépense:\n\n");

        for (CategoryRule cat : rulesConfig.getCategories()) {
            String emoji = getCategoryEmoji(cat.getName());
            sb.append(String.format("%s **%s** - Plafond: %.2f TND\n",
                    emoji, cat.getName(), cat.getPlafond()));
        }

        sb.append("\n💡 **Astuce:** Vous pouvez aussi dire directement ");
        sb.append("'créer note transport' ou 'ajouter hébergement'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(replies)
                .requiresAction(true)
                .actionType("SHOW_CREATE_FORM")
                .build();
    }
    /**
     * Unified method to filter notes by status for all user roles
     * @param request The chat request
     * @param status The status to filter by (EN_ATTENTE, VALIDEE, REFUSEE, REMBOURSEE)
     * @return Formatted response with filtered notes
     */
    private ChatResponse handleFilterNotesByStatus(ChatRequest request, String status) {
        log.info("📊 Filtrage des notes par statut: {} pour l'utilisateur: {} (rôle: {})",
                status, request.getUserId(), request.getUserRole());

        String userRole = request.getUserRole();

        try {
            List<?> notes;
            boolean isManager = "MANAGER".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole);

            // Récupérer les notes selon le rôle
            if (isManager) {
                Long departmentId = extractDepartmentFromToken();
                if (departmentId == null) {
                    return ChatResponse.builder()
                            .answer("❌ Impossible de déterminer votre département.")
                            .sessionId(request.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .build();
                }
                notes = expenseApiService.getNotesByDepartment(departmentId);
                log.info("📊 {} notes trouvées dans le département {}", ((List<?>)notes).size(), departmentId);
            } else {
                notes = expenseNoteRepository.findRecentByEmployee(request.getUserId());
                log.info("📊 {} notes trouvées pour l'employé {}", ((List<?>)notes).size(), request.getUserId());
            }

            if (notes == null || ((List<?>)notes).isEmpty()) {
                String message = isManager ?
                        "📭 Aucune note trouvée dans votre département." :
                        "📭 Vous n'avez aucune note de frais pour le moment.";
                return ChatResponse.builder()
                        .answer(message)
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // Filtrer par statut
            List<?> filteredNotes = ((List<?>)notes).stream()
                    .filter(note -> {
                        String noteStatus;
                        if (note instanceof ExpenseNote) {
                            noteStatus = ((ExpenseNote) note).getStatus();
                        } else if (note instanceof ExpenseNoteDTO) {
                            noteStatus = ((ExpenseNoteDTO) note).getStatus();
                        } else {
                            return false;
                        }
                        return status.equals(noteStatus);
                    })
                    .collect(Collectors.toList());

            if (filteredNotes.isEmpty()) {
                String statusMessage = getStatusMessageInFrench(status);
                String suggestion = isManager ?
                        "Essayez 'toutes les notes' pour voir toutes les notes." :
                        "Essayez 'mes notes' pour voir toutes vos notes.";

                return ChatResponse.builder()
                        .answer(String.format("📭 Aucune note %s trouvée.\n\n%s",
                                statusMessage.toLowerCase(), suggestion))
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .quickReplies(List.of(
                                QuickReply.builder().text(isManager ? "📋 Toutes les notes" : "📋 Mes notes")
                                        .payload(isManager ? "ALL_NOTES" : "VIEW_NOTES")
                                        .icon("📋").build(),
                                QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                        ))
                        .build();
            }

            // ✅ CONSTRUIRE LA RÉPONSE AVEC DES SAUTS DE LIGNE CORRECTS
            StringBuilder sb = new StringBuilder();
            String statusEmoji = getStatusEmojiForNote(status);
            String statusTitle = getStatusTitleInFrench(status);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            // Titre
            if (isManager) {
                sb.append(String.format("%s **Notes %s dans votre département** (%d)\n\n",
                        statusEmoji, statusTitle.toLowerCase(), filteredNotes.size()));
            } else {
                sb.append(String.format("%s **Vos notes %s** (%d)\n\n",
                        statusEmoji, statusTitle.toLowerCase(), filteredNotes.size()));
            }

            // Liste des notes
            filteredNotes.stream().limit(5).forEach(note -> {
                if (note instanceof ExpenseNote) {
                    ExpenseNote expenseNote = (ExpenseNote) note;
                    sb.append(String.format("%s **Note #%d**\n", statusEmoji, expenseNote.getId()));
                    sb.append(String.format("   💰 Montant: %.2f TND\n", expenseNote.getTotalAmount()));
                    sb.append(String.format("   📅 Date: %s\n", expenseNote.getCreatedAt().format(formatter)));

                    if (isManager) {
                        sb.append(String.format("   👤 Employé: %s\n", formatEmployeeId(expenseNote.getEmployeeId())));
                    }

                    int lineCount = expenseNote.getLines() != null ? expenseNote.getLines().size() : 0;
                    if (lineCount > 0) {
                        sb.append(String.format("   📋 %d ligne(s)\n", lineCount));
                    }
                    sb.append("\n"); // ✅ SAUT DE LIGNE ENTRE LES NOTES
                } else if (note instanceof ExpenseNoteDTO) {
                    ExpenseNoteDTO expenseNote = (ExpenseNoteDTO) note;
                    sb.append(String.format("%s **Note #%d**\n", statusEmoji, expenseNote.getId()));
                    sb.append(String.format("   💰 Montant: %.2f TND\n", expenseNote.getTotalAmount()));
                    sb.append(String.format("   📅 Date: %s\n",
                            expenseNote.getCreatedAt() != null ? expenseNote.getCreatedAt().format(formatter) : "N/A"));

                    if (isManager) {
                        sb.append(String.format("   👤 Employé: %s\n", formatEmployeeId(expenseNote.getEmployeeId())));
                    }

                    if (expenseNote.getProjectId() != null) {
                        sb.append(String.format("   📁 Projet ID: %d\n", expenseNote.getProjectId()));
                    }
                    sb.append("\n"); // ✅ SAUT DE LIGNE ENTRE LES NOTES
                }
            });

            if (filteredNotes.size() > 5) {
                sb.append(String.format("... et %d autre(s) note(s)\n\n", filteredNotes.size() - 5));
            } else {
                sb.append("\n"); // ✅ SAUT DE LIGNE SUPPLÉMENTAIRE
            }

            // Calculer le total
            double total = filteredNotes.stream()
                    .mapToDouble(note -> {
                        if (note instanceof ExpenseNote) {
                            return ((ExpenseNote) note).getTotalAmount();
                        } else if (note instanceof ExpenseNoteDTO) {
                            return ((ExpenseNoteDTO) note).getTotalAmount();
                        }
                        return 0;
                    })
                    .sum();
            sb.append(String.format("💰 **Total %s:** %.2f TND\n\n", statusTitle.toLowerCase(), total));

            // Suggestions
            sb.append("💡 **Pour voir plus de détails:**\n");
            sb.append("• `détails de la note #123`\n");
            if (!isManager) {
                sb.append("• `mes notes` pour voir toutes vos notes\n");
            } else {
                sb.append("• `toutes les notes` pour voir toutes les notes du département\n");
            }

            // ✅ FORMATER LA RÉPONSE AVEC RESPONSE FORMATTER
            String formattedAnswer = responseFormatter.formatResponse(
                    sb.toString(),
                    "FILTER_NOTES_BY_STATUS",
                    request.getUserRole()
            );

            // Quick replies contextuels
            List<QuickReply> quickReplies = new ArrayList<>();

            if (!"EN_ATTENTE".equals(status)) {
                quickReplies.add(QuickReply.builder()
                        .text("⏳ Notes en attente")
                        .payload("FILTER_EN_ATTENTE")
                        .icon("⏳")
                        .build());
            }
            if (!"VALIDEE".equals(status)) {
                quickReplies.add(QuickReply.builder()
                        .text("✅ Notes validées")
                        .payload("FILTER_VALIDEE")
                        .icon("✅")
                        .build());
            }
            if (!"REFUSEE".equals(status)) {
                quickReplies.add(QuickReply.builder()
                        .text("❌ Notes refusées")
                        .payload("FILTER_REFUSEE")
                        .icon("❌")
                        .build());
            }
            if (!"REMBOURSEE".equals(status)) {
                quickReplies.add(QuickReply.builder()
                        .text("💰 Notes remboursées")
                        .payload("FILTER_REMBOURSEE")
                        .icon("💰")
                        .build());
            }

            quickReplies.add(QuickReply.builder()
                    .text(isManager ? "📋 Toutes les notes" : "📋 Mes notes")
                    .payload(isManager ? "ALL_NOTES" : "VIEW_NOTES")
                    .icon("📋")
                    .build());
            quickReplies.add(QuickReply.builder()
                    .text("❓ Aide")
                    .payload("HELP")
                    .icon("❓")
                    .build());

            return ChatResponse.builder()
                    .answer(formattedAnswer)  // ✅ RÉPONSE FORMATÉE
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .quickReplies(quickReplies)
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors du filtrage des notes par statut {}: {}", status, e.getMessage(), e);
            return ChatResponse.builder()
                    .answer("❌ Une erreur est survenue lors de la récupération des notes. Veuillez réessayer.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Helper method to get French status message
     */
    private String getStatusMessageInFrench(String status) {
        return switch (status) {
            case "EN_ATTENTE" -> "en attente";
            case "VALIDEE" -> "validée";
            case "REFUSEE" -> "refusée";
            case "REMBOURSEE" -> "remboursée";
            default -> "";
        };
    }

    /**
     * Helper method to get French status title
     */
    private String getStatusTitleInFrench(String status) {
        return switch (status) {
            case "EN_ATTENTE" -> "En attente";
            case "VALIDEE" -> "Validées";
            case "REFUSEE" -> "Refusées";
            case "REMBOURSEE" -> "Remboursées";
            default -> "";
        };
    }
    private ChatResponse handleViewNotes(ChatRequest request) {
        log.info("📋 Récupération des notes pour l'utilisateur: {}", request.getUserId());

        List<ExpenseNote> notes = expenseNoteRepository.findRecentByEmployee(request.getUserId());

        if (notes == null || notes.isEmpty()) {
            log.info("📭 Aucune note trouvée pour l'utilisateur: {}", request.getUserId());
            return ChatResponse.builder()
                    .answer("📭 Vous n'avez aucune note de frais pour le moment. Souhaitez-vous en créer une ?")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .quickReplies(List.of(
                            QuickReply.builder().text("Créer une note").payload("CREATE_NOTE").icon("➕").build()
                    ))
                    .build();
        }

        log.info("✅ {} notes trouvées pour l'utilisateur", notes.size());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Vos notes de frais**\n\n");

        notes.stream().limit(5).forEach(note -> {
            String statusEmoji = getStatusEmojiForNote(note.getStatus());
            sb.append(String.format("%s **Note #%d**\n", statusEmoji, note.getId()));
            sb.append(String.format("   💰 Montant: %.2f TND\n", note.getTotalAmount()));
            sb.append(String.format("   📅 Date: %s\n", note.getCreatedAt().format(formatter)));
            sb.append(String.format("   📊 Statut: %s %s\n", note.getStatus(), statusEmoji));

            int lineCount = note.getLines() != null ? note.getLines().size() : 0;
            if (lineCount > 0) {
                sb.append(String.format("   📋 %d ligne(s)\n", lineCount));
            }
            sb.append("\n");
        });

        if (notes.size() > 5) {
            sb.append(String.format("... et %d autre(s) note(s)", notes.size() - 5));
        }

        double total = notes.stream()
                .mapToDouble(ExpenseNote::getTotalAmount)
                .sum();
        sb.append(String.format("\n💰 **Total toutes notes:** %.2f TND", total));

        // ✅ FORMATER LA RÉPONSE
        String formattedAnswer = responseFormatter.formatResponse(
                sb.toString(),
                "VIEW_NOTES",
                request.getUserRole()
        );

        return ChatResponse.builder()
                .answer(formattedAnswer)  // ✅ RÉPONSE FORMATÉE
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(List.of(
                        QuickReply.builder().text("Voir statut").payload("NOTE_STATUS").icon("ℹ️").build(),
                        QuickReply.builder().text("Total dépenses").payload("TOTAL_AMOUNT").icon("💰").build(),
                        QuickReply.builder().text("Créer une note").payload("CREATE_NOTE").icon("➕").build()
                ))
                .build();
    }

    private ChatResponse handleNoteStatus(ChatRequest request, Long contextNoteId) {
        // Utiliser le contexte si fourni
        Long noteId = contextNoteId != null ? contextNoteId : extractNoteId(request.getQuestion());

        if (noteId == null) {
            String response = "Pour connaître le statut d'une note, veuillez préciser son numéro\n\nExemples:\n• 'statut note 123'\n• 'où en est la note 45?'";
            String formatted = responseFormatter.formatResponse(response, "NOTE_STATUS", request.getUserRole());
            return ChatResponse.builder()
                    .answer(formatted)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        Optional<ExpenseNote> noteOpt = expenseNoteRepository.findById(noteId);

        if (noteOpt.isEmpty()) {
            String response = "❌ Désolé, je n'ai pas trouvé la note #" + noteId;
            String formatted = responseFormatter.formatResponse(response, "NOTE_STATUS", request.getUserRole());
            return ChatResponse.builder()
                    .answer(formatted)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        ExpenseNote note = noteOpt.get();

        if (!note.getEmployeeId().equals(request.getUserId())) {
            String response = "❌ Vous n'êtes pas autorisé à voir cette note.";
            String formatted = responseFormatter.formatResponse(response, "NOTE_STATUS", request.getUserRole());
            return ChatResponse.builder()
                    .answer(formatted)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        String statusEmoji = getStatusEmojiForNote(note.getStatus());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s **Note #%d** - %s %s\n\n",
                statusEmoji, note.getId(), note.getStatus(), statusEmoji));
        sb.append(String.format("💰 **Montant:** %.2f TND\n", note.getTotalAmount()));
        sb.append(String.format("📅 **Créée le:** %s\n", note.getCreatedAt().format(formatter)));

        if (note.getUpdatedAt() != null) {
            sb.append(String.format("🔄 **Mise à jour:** %s\n", note.getUpdatedAt().format(formatter)));
        }

        List<ExpenseLine> lines = note.getLines();
        if (lines != null && !lines.isEmpty()) {
            sb.append("\n📋 **Lignes:**\n");
            for (ExpenseLine line : lines) {
                String categoryName = getCategoryNameById(line.getCategoryId());
                sb.append(String.format("  • %s: %.2f TND\n", categoryName, line.getAmount()));
            }
        }

        // ✅ FORMATER LA RÉPONSE
        String formattedAnswer = responseFormatter.formatResponse(
                sb.toString(),
                "NOTE_STATUS",
                request.getUserRole()
        );

        return ChatResponse.builder()
                .answer(formattedAnswer)
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }
    /**
     * Handle quick reply for filtered notes
     */
    public ChatResponse handleFilteredNotesByStatus(String status, ChatRequest request) {
        return handleFilterNotesByStatus(request, status);
    }
// Ajouter cette méthode dans ChatbotServiceImpl.java

    private ChatResponse handleNoteDetails(ChatRequest request, Long contextNoteId) {
        // Utiliser le contexte si fourni
        Long noteId = contextNoteId != null ? contextNoteId : extractNoteId(request.getQuestion());

        if (noteId == null) {
            String response = "Pour voir les détails d'une note, veuillez préciser son numéro.\n\nExemples:\n• 'détails de la note #92'\n• 'afficher la note 123'\n• 'lister la note 45'";
            String formatted = responseFormatter.formatResponse(response, "NOTE_DETAILS", request.getUserRole());
            return ChatResponse.builder()
                    .answer(formatted)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        log.info("🔍 Récupération des détails de la note #{} pour l'utilisateur {}", noteId, request.getUserId());

        Optional<ExpenseNote> noteOpt = expenseNoteRepository.findById(noteId);

        if (noteOpt.isEmpty()) {
            String response = "❌ La note #" + noteId + " n'existe pas.";
            String formatted = responseFormatter.formatResponse(response, "NOTE_DETAILS", request.getUserRole());
            return ChatResponse.builder()
                    .answer(formatted)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        ExpenseNote note = noteOpt.get();

        boolean hasAccess = note.getEmployeeId().equals(request.getUserId()) ||
                "MANAGER".equalsIgnoreCase(request.getUserRole()) ||
                "ADMIN".equalsIgnoreCase(request.getUserRole());

        if (!hasAccess) {
            log.warn("⛔ Accès refusé pour l'utilisateur {} à la note #{}", request.getUserId(), noteId);
            String response = "❌ Vous n'êtes pas autorisé à consulter cette note.";
            String formatted = responseFormatter.formatResponse(response, "NOTE_DETAILS", request.getUserRole());
            return ChatResponse.builder()
                    .answer(formatted)
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 **Détails de la note #").append(noteId).append("**\n\n");
        sb.append(String.format("• 💰 **Montant total:** %.2f TND\n", note.getTotalAmount()));
        sb.append(String.format("• 📊 **Statut:** %s %s\n", note.getStatus(), getStatusEmojiForNote(note.getStatus())));
        sb.append(String.format("• 📅 **Créée le:** %s\n", formatDate(note.getCreatedAt())));

        if (note.getUpdatedAt() != null && !note.getUpdatedAt().equals(note.getCreatedAt())) {
            sb.append(String.format("• 🔄 **Mise à jour:** %s\n", formatDate(note.getUpdatedAt())));
        }

        List<ExpenseLine> lines = note.getLines();
        if (lines != null && !lines.isEmpty()) {
            sb.append("\n📋 **Lignes de la note:**\n");
            int lineNumber = 1;
            for (ExpenseLine line : lines) {
                String categoryName = getCategoryNameById(line.getCategoryId());
                sb.append(String.format("  • **Ligne %d:** [%s] %.2f TND", lineNumber++, categoryName, line.getAmount()));

                if (line.getDescription() != null && !line.getDescription().isEmpty()) {
                    sb.append(" - ").append(line.getDescription());
                }

                List<String> details = new ArrayList<>();
                if (line.getNombrePersonnes() != null && line.getNombrePersonnes() > 0) {
                    details.add(line.getNombrePersonnes() + " personnes");
                }
                if (line.getNombreNuits() != null && line.getNombreNuits() > 0) {
                    details.add(line.getNombreNuits() + " nuits");
                }
                if (line.getRepasType() != null && !line.getRepasType().isEmpty()) {
                    details.add("Repas: " + line.getRepasType());
                }
                if (line.getDepart() != null && line.getDestination() != null) {
                    details.add(line.getDepart() + " → " + line.getDestination());
                }

                if (!details.isEmpty()) {
                    sb.append(" (").append(String.join(", ", details)).append(")");
                }

                if (line.getJustificatifPath() != null && !line.getJustificatifPath().isEmpty()) {
                    sb.append(" 📎");
                }
                sb.append("\n");
            }
        } else {
            sb.append("\n📋 Aucune ligne de détail pour cette note.");
        }

        // ✅ FORMATER LA RÉPONSE
        String formattedAnswer = responseFormatter.formatResponse(
                sb.toString(),
                "NOTE_DETAILS",
                request.getUserRole()
        );

        return ChatResponse.builder()
                .answer(formattedAnswer)
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(List.of(
                        QuickReply.builder().text("📊 Statut").payload("NOTE_STATUS_" + noteId).icon("ℹ️").build(),
                        QuickReply.builder().text("📋 Mes notes").payload("VIEW_NOTES").icon("📋").build(),
                        QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                ))
                .build();
    }
    private ChatResponse handleTotalAmount(ChatRequest request) {
        List<ExpenseNoteDTO> notes = expenseApiService.getNotesByEmployee(request.getUserId());

        double total = notes.stream()
                .filter(n -> "VALIDEE".equals(n.getStatus()) || "REMBOURSEE".equals(n.getStatus()))
                .mapToDouble(ExpenseNoteDTO::getTotalAmount)
                .sum();

        Map<String, Double> totalsByStatus = notes.stream()
                .collect(Collectors.groupingBy(
                        ExpenseNoteDTO::getStatus,
                        Collectors.summingDouble(ExpenseNoteDTO::getTotalAmount)
                ));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("💰 **Total des dépenses: %.2f TND**\n\n", total));
        sb.append("📊 **Détail par statut:**\n");

        totalsByStatus.forEach((status, amount) -> {
            String emoji = getStatusEmoji(status);
            sb.append(String.format("%s %s: %.2f TND\n", emoji, status, amount));
        });

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }
    // Helper pour obtenir le nom d'une catégorie
    private String getCategoryNameById(Long categoryId) {
        if (categoryId == null) return "Catégorie inconnue";
        Optional<Category> category = categoryRepository.findById(categoryId);
        return category.map(Category::getName).orElse("Catégorie " + categoryId);
    }
    private ChatResponse handleCategoryPlafond(ChatRequest request) {
        log.info("💰 Récupération des plafonds depuis la BASE DE DONNÉES");

        String categoryName = extractCategoryName(request.getQuestion());

        List<Category> categories = categoryRepository.findByActiveTrue();
        log.info("📊 {} catégories trouvées en base", categories.size());

        if (categories.isEmpty()) {
            log.warn("⚠️ Aucune catégorie trouvée en base, fallback vers configuration");
            return buildCategoryPlafondFromConfig(request);
        }

        if (categoryName != null) {
            log.info("🔍 Recherche de la catégorie spécifique: {}", categoryName);

            Optional<Category> categoryOpt = categories.stream()
                    .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                    .findFirst();

            if (categoryOpt.isEmpty()) {
                categoryOpt = categories.stream()
                        .filter(c -> c.getName().toLowerCase().contains(categoryName.toLowerCase()) ||
                                categoryName.toLowerCase().contains(c.getName().toLowerCase()))
                        .findFirst();
            }

            if (categoryOpt.isPresent()) {
                Category category = categoryOpt.get();
                log.info("✅ Catégorie trouvée: {} - Plafond: {} TND",
                        category.getName(), category.getPlafond());

                ChatResponse response = buildCategoryDetailResponseFromDatabase(category, request);

                // ✅ FORMATER LA RÉPONSE
                String formattedAnswer = responseFormatter.formatResponse(
                        response.getAnswer(),
                        "CATEGORY_PLAFOND",
                        request.getUserRole()
                );
                response.setAnswer(formattedAnswer);
                return response;
            }

            log.warn("⚠️ Catégorie non trouvée: {}", categoryName);

            List<String> similarCategories = categories.stream()
                    .map(Category::getName)
                    .filter(name -> name.toLowerCase().contains(categoryName.toLowerCase()) ||
                            categoryName.toLowerCase().contains(name.toLowerCase()))
                    .limit(3)
                    .collect(Collectors.toList());

            if (!similarCategories.isEmpty()) {
                String response = String.format("❌ La catégorie **%s** n'existe pas.\n\n💡 Catégories similaires :\n%s\n\n💡 Pour voir tous les plafonds, dites 'tous les plafonds'",
                        categoryName,
                        similarCategories.stream().map(n -> "• " + n).collect(Collectors.joining("\n")));

                String formatted = responseFormatter.formatResponse(response, "CATEGORY_PLAFOND", request.getUserRole());
                return ChatResponse.builder()
                        .answer(formatted)
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            return showAllPlafonds(categories, request);
        }

        if (shouldShowAllPlafonds(request.getQuestion())) {
            return showAllPlafonds(categories, request);
        }

        return showAllPlafonds(categories, request);
    }
    /**
     * ✅ BUG 1: Affiche tous les plafonds (pour la question "plafond" seul)
     */
    private ChatResponse handleAllPlafonds(ChatRequest request) {
        List<Category> categories = categoryRepository.findByActiveTrue();

        if (categories.isEmpty()) {
            return ChatResponse.builder()
                    .answer("❌ Aucune catégorie trouvée.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        // Grouper par nom pour éviter les doublons
        Map<String, Double> uniqueCategories = new LinkedHashMap<>();
        for (Category cat : categories) {
            uniqueCategories.put(cat.getName(), cat.getPlafond());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("💰 **Plafonds par catégorie**\n\n");

        for (Map.Entry<String, Double> entry : uniqueCategories.entrySet()) {
            String emoji = getCategoryEmoji(entry.getKey());
            sb.append(String.format("%s **%s**: %.2f TND\n", emoji, entry.getKey(), entry.getValue()));
        }

        // Statistiques
        Double avgPlafond = categoryRepository.getAveragePlafond();
        Double maxPlafond = categoryRepository.getMaxPlafond();

        if (avgPlafond != null && maxPlafond != null) {
            sb.append("\n📊 **Statistiques:**\n");
            sb.append(String.format("• Plafond moyen: %.2f TND\n", avgPlafond));
            sb.append(String.format("• Plafond maximum: %.2f TND\n", maxPlafond));
        }

        sb.append("\n💡 **Pour plus de détails:** dites par exemple:\n");
        sb.append("• 'plafond restauration'\n");
        sb.append("• 'plafond transport'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }
    /**
     * Vérifie si la question demande explicitement tous les plafonds
     */
    private boolean shouldShowAllPlafonds(String question) {
        String lower = question.toLowerCase();
        return lower.contains("tous les plafonds") ||
                lower.contains("tous plafonds") ||
                lower.equals("plafonds") ||
                lower.contains("liste des plafonds") ||
                lower.contains("quels sont les plafonds");
    }

    /**
     * Affiche tous les plafonds (utilisé uniquement quand demandé explicitement)
     */
    private ChatResponse showAllPlafonds(List<Category> categories, ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("💰 **Plafonds par catégorie**\n\n");

        for (Category cat : categories) {
            String emoji = getCategoryEmoji(cat.getName());
            sb.append(String.format("%s **%s**: %.2f TND\n", emoji, cat.getName(), cat.getPlafond()));
        }

        // Ajouter les statistiques
        Double avgPlafond = categoryRepository.getAveragePlafond();
        Double maxPlafond = categoryRepository.getMaxPlafond();

        if (avgPlafond != null && maxPlafond != null) {
            sb.append("\n📊 **Statistiques:**\n");
            sb.append(String.format("• Plafond moyen: %.2f TND\n", avgPlafond));
            sb.append(String.format("• Plafond maximum: %.2f TND\n", maxPlafond));
        }

        sb.append("\n💡 **Pour plus de détails:** dites par exemple:\n");
        sb.append("• 'plafond transport'\n");
        sb.append("• 'plafond hébergement'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(categories.stream()
                        .limit(3)
                        .map(cat -> QuickReply.builder()
                                .text(cat.getName())
                                .payload("CATEGORY_" + cat.getId())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
    /**
     * Trouve une catégorie par mots-clés
     */
    private Category findCategoryByKeywords(String keyword) {
        List<Category> categories = categoryRepository.findByActiveTrue();

        Map<String, String> keywordMap = Map.of(
                "restauration", "Restauration",
                "repas", "Restauration",
                "hébergement", "Hébergement",
                "hebergement", "Hébergement",
                "hotel", "Hébergement",
                "hôtel", "Hébergement",
                "transport", "Transport",
                "voiture", "Transport",
                "carburant", "Carburant",
                "essence", "Carburant"
        );

        String targetName = keywordMap.get(keyword.toLowerCase());
        if (targetName != null) {
            return categories.stream()
                    .filter(c -> c.getName().equalsIgnoreCase(targetName))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /**
     * Fallback vers la configuration si la base est vide
     */
    private ChatResponse buildCategoryPlafondFromConfig(ChatRequest request) {
        log.info("📋 Utilisation des plafonds depuis la configuration (fallback)");

        StringBuilder sb = new StringBuilder();
        sb.append("💰 **Plafonds par catégorie (Configuration)**\n\n");

        for (CategoryRule cat : rulesConfig.getCategories()) {
            String emoji = getCategoryEmoji(cat.getName());
            sb.append(String.format("%s **%s**: %.2f TND\n", emoji, cat.getName(), cat.getPlafond()));

            if (cat.getFields() != null && !cat.getFields().isEmpty()) {
                List<String> requiredFields = cat.getFields().stream()
                        .filter(CategoryRule.CategoryField::isRequired)
                        .map(CategoryRule.CategoryField::getLabel)
                        .collect(Collectors.toList());

                if (!requiredFields.isEmpty()) {
                    sb.append(String.format("   📋 *Champs requis:* %s\n",
                            String.join(", ", requiredFields)));
                }
            }
            sb.append("\n");
        }

        sb.append("💡 **Pour plus de détails:** dites par exemple:\n");
        sb.append("• 'plafond transport'\n");
        sb.append("• 'règles hébergement'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleSpecialRules(ChatRequest request) {
        String categoryName = extractCategoryName(request.getQuestion());

        if (categoryName != null) {
            CategoryRule category = rulesConfig.getCategoryByName(categoryName);
            if (category != null && category.getSpecialRules() != null && !category.getSpecialRules().isEmpty()) {
                return buildSpecialRulesResponse(category, request);
            } else if (category != null) {
                return ChatResponse.builder()
                        .answer(String.format("Aucune règle spéciale pour la catégorie **%s**.", category.getName()))
                        .sessionId(request.getSessionId())
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ **Règles spéciales par catégorie**\n\n");

        for (CategoryRule cat : rulesConfig.getCategories()) {
            if (cat.getSpecialRules() != null && !cat.getSpecialRules().isEmpty()) {
                sb.append(String.format("**%s:**\n", cat.getName()));
                for (String rule : cat.getSpecialRules()) {
                    sb.append(String.format("  • %s\n", rule));
                }
                sb.append("\n");
            }
        }

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleAlertRules(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("🚨 **Système d'alertes automatiques**\n\n");

        for (AlertRule alert : rulesConfig.getAlertRules()) {
            String severityEmoji = switch (alert.getSeverity()) {
                case "danger" -> "🔴";
                case "warning" -> "🟡";
                default -> "🔵";
            };

            sb.append(String.format("%s **%s**\n", severityEmoji, alert.getName()));
            sb.append(String.format("   📝 %s\n", alert.getDescription()));
            if (alert.getAction() != null) {
                sb.append(String.format("   ⚡ Action: %s\n", alert.getAction()));
            }
            sb.append("\n");
        }

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleWorkflow(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 **Workflow de validation**\n\n");

        for (WorkflowStep step : rulesConfig.getWorkflowSteps()) {
            String roleEmoji = switch (step.getRole()) {
                case "EMPLOYEE" -> "👤";
                case "MANAGER" -> "👔";
                case "ADMIN" -> "⚙️";
                default -> "📋";
            };

            sb.append(String.format("%d. %s **%s**\n", step.getStep(), roleEmoji, step.getName()));
            sb.append(String.format("   %s\n", step.getDescription()));
        }

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleFAQ(ChatRequest request) {
        // Check if asking for a specific FAQ
        String question = request.getQuestion().toLowerCase();

        if (question.contains("partiel") || question.contains("dépassement")) {
            return ChatResponse.builder()
                    .answer("**Q:** Comment sont calculés les remboursements partiels ?\n\n" +
                            "**R:** Si votre dépense dépasse le plafond de la catégorie, vous serez remboursé " +
                            "uniquement jusqu'à concurrence du plafond. La différence reste à votre charge.\n\n" +
                            "Exemple: Dépense de 200 TND avec plafond 150 TND → remboursement 150 TND")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        if (question.contains("illisible") || question.contains("qualité")) {
            return ChatResponse.builder()
                    .answer("**Q:** Que faire si mon justificatif est illisible ?\n\n" +
                            "**R:** Le système générera une alerte 'JUSTIFICATIF_ILLISIBLE'. Vous pouvez quand même " +
                            "soumettre la note, mais elle sera examinée manuellement par l'administrateur. " +
                            "Il est recommandé de rescanner le document en meilleure qualité.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        if (question.contains("sans justificatif") || question.contains("sans facture")) {
            return ChatResponse.builder()
                    .answer("**Q:** Puis-je être remboursé sans justificatif ?\n\n" +
                            "**R:** Non, un justificatif valide est obligatoire pour chaque ligne de frais. " +
                            "Sans justificatif, la note sera refusée.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        if (question.contains("délai") || question.contains("temps")) {
            return ChatResponse.builder()
                    .answer("**Q:** Combien de temps faut-il pour être remboursé ?\n\n" +
                            "**R:** Le délai standard est de **5 jours ouvrables** après validation complète " +
                            "(manager + admin).")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        // Show all FAQs
        StringBuilder sb = new StringBuilder();
        sb.append("❓ **Questions fréquentes**\n\n");

        for (int i = 0; i < rulesConfig.getFaqs().size(); i++) {
            FAQ faq = rulesConfig.getFaqs().get(i);
            sb.append(String.format("%d. %s\n", i + 1, faq.getQuestion()));
        }

        sb.append("\n💡 Pour voir la réponse, dites par exemple:");
        sb.append("\n• 'remboursement partiel'");
        sb.append("\n• 'justificatif illisible'");
        sb.append("\n• 'délai remboursement'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleHolidayRules(ChatRequest request) {
        String[] holidayNames = {
                "1er janvier (Nouvel an)",
                "14 janvier (Fête de la Révolution)",
                "20 mars (Fête de l'Indépendance)",
                "9 avril (Fête des Martyrs)",
                "1er mai (Fête du Travail)",
                "25 juillet (Fête de la République)",
                "13 août (Fête de la Femme)",
                "15 octobre (Fête de l'Évacuation)",
                "17 décembre (Fête de la Révolution)"
        };

        StringBuilder sb = new StringBuilder();
        sb.append("📅 **Jours non autorisés pour les dépenses**\n\n");
        sb.append("❌ **Week-ends:** Samedi et dimanche\n\n");
        sb.append("❌ **Jours fériés tunisiens:**\n");

        for (String name : holidayNames) {
            sb.append(String.format("  • %s\n", name));
        }

        sb.append("\n✅ **Jours autorisés:** Lundi au vendredi (hors jours fériés)");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleCurrency(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("💱 **Devises disponibles**\n\n");

        for (Currency curr : rulesConfig.getCurrencies()) {
            sb.append(String.format("%s **%s** (%s)\n", curr.getSymbol(), curr.getCode(), curr.getName()));
            sb.append(String.format("   Taux: 1 TND = %.2f %s\n", curr.getRate(), curr.getCode()));
        }

        sb.append("\n💡 Pour changer de devise, dites par exemple:");
        sb.append("\n• 'utiliser euro'");
        sb.append("\n• 'changer en dollar'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(rulesConfig.getCurrencies().stream()
                        .map(curr -> QuickReply.builder()
                                .text(curr.getCode())
                                .payload("CURRENCY_" + curr.getCode())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private ChatResponse handleValidationRules(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Règles de validation des notes de frais**\n\n");

        sb.append("**📌 Principes généraux:**\n");
        for (GeneralRule rule : rulesConfig.getGeneralRulesByCategory("Principes généraux")) {
            sb.append(String.format("  • %s\n", rule.getRule()));
        }

        sb.append("\n**📎 Règles justificatifs:**\n");
        for (GeneralRule rule : rulesConfig.getGeneralRulesByCategory("Règles justificatifs")) {
            sb.append(String.format("  • %s\n", rule.getRule()));
        }

        sb.append("\n**💰 Plafonds:**\n");
        sb.append(String.format("  • Plafond moyen: %.2f TND\n", rulesConfig.getAveragePlafond()));
        sb.append(String.format("  • Plafond maximum: %.2f TND\n", rulesConfig.getMaxPlafond()));

        // ✅ UTILISER formatRulesResponse POUR LES RÈGLES
        String formattedAnswer = responseFormatter.formatRulesResponse(sb.toString(), request.getUserRole());

        return ChatResponse.builder()
                .answer(formattedAnswer)
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(List.of(
                        QuickReply.builder().text("Plafonds").payload("CATEGORY_PLAFOND").icon("cash-coin").build(),
                        QuickReply.builder().text("Alertes").payload("ALERT_RULES").icon("exclamation-triangle").build(),
                        QuickReply.builder().text("Jours fériés").payload("HOLIDAY_RULES").icon("calendar-x").build()
                ))
                .build();
    }

    private ChatResponse handleUploadFile(ChatRequest request) {
        return ChatResponse.builder()
                .answer("""
                    **📁 Règles pour les justificatifs**

                    **Formats acceptés:**
                    • PDF, JPG, PNG, DOC, DOCX

                    **Taille:**
                    • Maximum 10 Mo par fichier

                    **Organisation:**
                    • Un justificatif par ligne de frais
                    • Dossier 'accords' pour les accords préalables
                    • Dossier 'factures' pour les reçus

                    **⚠️ Alertes:**
                    • Justificatif illisible → ALERTE
                    • Conservation: 6 mois

                    **💡 Bonnes pratiques:**
                    • Nommez vos fichiers de façon descriptive
                    • Évitez les caractères spéciaux
                    • Scannez en haute résolution""")
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .requiresAction(true)
                .actionType("SHOW_UPLOAD")
                .build();
    }

    private ChatResponse handleFileInfo(ChatRequest request) {
        return handleUploadFile(request);
    }

    private ChatResponse handleDeleteNote(ChatRequest request) {
        Long noteId = extractNoteId(request.getQuestion());

        if (noteId == null) {
            return ChatResponse.builder()
                    .answer("Pour supprimer une note, veuillez préciser son numéro\n\nExemple: 'supprimer note 123'")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        ExpenseNoteDTO note = expenseApiService.getNoteById(noteId);

        if (note == null) {
            return ChatResponse.builder()
                    .answer("❌ Note #" + noteId + " introuvable.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        if (!note.getEmployeeId().equals(request.getUserId())) {
            return ChatResponse.builder()
                    .answer("❌ Vous n'êtes pas autorisé à supprimer cette note.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        if (!"EN_ATTENTE".equals(note.getStatus())) {
            return ChatResponse.builder()
                    .answer("❌ Impossible de supprimer cette note car son statut est **" + note.getStatus() + "**.\n" +
                            "Seules les notes en attente peuvent être supprimées.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        return ChatResponse.builder()
                .answer(String.format("⚠️ Êtes-vous sûr de vouloir supprimer la note #%d ? Cette action est irréversible.", noteId))
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .requiresAction(true)
                .actionType("CONFIRM_DELETE")
                .actionData(Map.of("noteId", noteId))
                .quickReplies(List.of(
                        QuickReply.builder().text("✅ Oui, supprimer").payload("CONFIRM_DELETE_" + noteId).icon("check-circle").build(),
                        QuickReply.builder().text("❌ Non, annuler").payload("CANCEL").icon("x-circle").build()
                ))
                .build();
    }

    private ChatResponse handleFilterByDate(ChatRequest request) {
        Map<String, LocalDate> dateRange = extractDateRange(request.getQuestion());

        if (dateRange.containsKey("start") && dateRange.containsKey("end")) {
            LocalDate start = dateRange.get("start");
            LocalDate end = dateRange.get("end");

            List<ExpenseNoteDTO> notes = expenseApiService.getNotesByEmployee(request.getUserId());

            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.plusDays(1).atStartOfDay();

            List<ExpenseNoteDTO> filtered = notes.stream()
                    .filter(n -> !n.getCreatedAt().isBefore(startDateTime) &&
                            n.getCreatedAt().isBefore(endDateTime))
                    .collect(Collectors.toList());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📅 **Notes du %s au %s**\n\n",
                    start.format(formatter), end.format(formatter)));

            if (filtered.isEmpty()) {
                sb.append("Aucune note trouvée pour cette période.");
            } else {
                for (ExpenseNoteDTO note : filtered) {
                    String statusEmoji = getStatusEmoji(note.getStatus());
                    sb.append(String.format("%s #%d: %.2f TND - %s\n",
                            statusEmoji, note.getId(), note.getTotalAmount(), note.getStatus()));
                }
                double total = filtered.stream().mapToDouble(ExpenseNoteDTO::getTotalAmount).sum();
                sb.append(String.format("\n💰 **Total période:** %.2f TND", total));
            }

            return ChatResponse.builder()
                    .answer(sb.toString())
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        return ChatResponse.builder()
                .answer("Pour filtrer par date, précisez une période\n\nExemple: 'dépenses entre 01/01/2024 et 31/01/2024'")
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse handleAdminActions(ChatRequest request) {
        if (!"ADMIN".equalsIgnoreCase(request.getUserRole())) {
            return ChatResponse.builder()
                    .answer("❌ Cette action est réservée aux administrateurs.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        String question = request.getQuestion().toLowerCase();

        // Check for reimburse command
        if (question.contains("rembourser")) {
            Long noteId = extractNoteId(question);
            if (noteId != null) {
                String comment = extractComment(question);
                boolean success = expenseApiService.reimburseNote(noteId, comment);

                if (success) {
                    return ChatResponse.builder()
                            .answer(String.format("💰 Note #%d marquée comme remboursée%s",
                                    noteId, comment != null ? " (commentaire: " + comment + ")" : ""))
                            .sessionId(request.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .build();
                } else {
                    return ChatResponse.builder()
                            .answer("❌ Erreur lors du remboursement de la note #" + noteId)
                            .sessionId(request.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .build();
                }
            }
        }

        // Check for admin reject command
        if (question.contains("refuser") && question.contains("validée")) {
            Long noteId = extractNoteId(question);
            if (noteId != null) {
                String comment = extractComment(question);
                if (comment == null) {
                    comment = "Refusé par admin";
                }
                boolean success = expenseApiService.adminRejectNote(noteId, comment);

                if (success) {
                    return ChatResponse.builder()
                            .answer(String.format("❌ Note #%d refusée par admin (commentaire: %s)", noteId, comment))
                            .sessionId(request.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .build();
                } else {
                    return ChatResponse.builder()
                            .answer("❌ Erreur lors du refus de la note #" + noteId)
                            .sessionId(request.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .build();
                }
            }
        }

        // Show validated notes waiting for reimbursement
        List<ExpenseNoteDTO> validatedNotes = expenseApiService.getNotesByStatus("VALIDEE");

        if (validatedNotes.isEmpty()) {
            return ChatResponse.builder()
                    .answer("Aucune note validée en attente de remboursement.")
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        double total = validatedNotes.stream()
                .mapToDouble(ExpenseNoteDTO::getTotalAmount)
                .sum();

        StringBuilder sb = new StringBuilder();
        sb.append("💰 **Notes validées en attente de remboursement**\n\n");

        validatedNotes.stream().limit(5).forEach(note -> {
            sb.append(String.format("• #%d - Employé: %s - %.2f TND\n",
                    note.getId(), note.getEmployeeId(), note.getTotalAmount()));
        });

        if (validatedNotes.size() > 5) {
            sb.append(String.format("\n... et %d autre(s) note(s)", validatedNotes.size() - 5));
        }

        sb.append(String.format("\n\n**Total à rembourser: %.2f TND**\n\n", total));

        sb.append("**Commandes:**\n");
        sb.append("• 'rembourser note 123'\n");
        sb.append("• 'refuser note 123 motif' (pour notes validées)");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .requiresAction(true)
                .actionType("SHOW_REIMBURSEMENTS")
                .build();
    }

    private ChatResponse handleHelp(ChatRequest request) {
        String role = request.getUserRole().toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("🤖 **Assistant Coral.io - Aide**\n\n");
        sb.append("Je peux vous aider avec:\n\n");

        if ("employee".equals(role)) {
            sb.append("👤 **Employé:**\n");
            sb.append("• Créer une note: 'créer note'\n");
            sb.append("• Voir mes notes: 'mes notes'\n");
            sb.append("• Statut note: 'statut note 123'\n");
            sb.append("• Plafonds: 'plafond transport'\n");
            sb.append("• Règles: 'règles justificatifs'\n");
            sb.append("• Total: 'total dépenses'\n");
        } else if ("manager".equals(role)) {
            sb.append("👔 **Manager:**\n");
            sb.append("• Notes en attente: 'notes en attente'\n");
            sb.append("• Toutes les notes: 'toutes les notes'\n");
            sb.append("• Détails d'une note: 'détails note 123'\n");
            sb.append("• Workflow: 'workflow validation'\n");
        } else if ("admin".equals(role)) {
            sb.append("⚙️ **Admin:**\n");
            sb.append("• Remboursements: 'notes à rembourser'\n");
            sb.append("• Rembourser: 'rembourser note 123'\n");
            sb.append("• Alertes: 'alertes système'\n");
            sb.append("• Statistiques: 'total à rembourser'\n");
        }

        sb.append("\n💡 **Questions générales:**\n");
        sb.append("• 'plafonds'\n");
        sb.append("• 'règles'\n");
        sb.append("• 'FAQ'\n");
        sb.append("• 'jours fériés'\n");
        sb.append("• 'devises'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(List.of(
                        QuickReply.builder().text("Plafonds").payload("CATEGORY_PLAFOND").icon("cash-coin").build(),
                        QuickReply.builder().text("Règles").payload("VALIDATION_RULES").icon("file-text").build(),
                        QuickReply.builder().text("FAQ").payload("FAQ").icon("question-circle").build()
                ))
                .build();
    }

    @Override
    public ChatResponse getHelp(String userId, String userRole) {
        return handleHelp(ChatRequest.builder()
                .userId(userId)
                .userRole(userRole)
                .question("aide")
                .build());
    }

    @Override
    public ChatResponse getContextualHelp(String userId, String userRole, String context) {
        ChatRequest request = ChatRequest.builder()
                .userId(userId)
                .userRole(userRole)
                .context(Map.of("context", context))
                .question("aide contextuelle")
                .build();

        return switch (context) {
            case "CREATE_NOTE" -> handleCreateNote(request);
            case "UPLOAD" -> handleUploadFile(request);
            case "VALIDATION" -> handleValidationRules(request);
            case "PLAFONDS" -> handleCategoryPlafond(request);
            default -> getHelp(userId, userRole);
        };
    }

    // ==================== UTILITY METHODS ====================

    private ChatResponse buildCategoryDetailResponse(CategoryRule category, ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        String emoji = getCategoryEmoji(category.getName());
        sb.append(String.format("%s **%s**\n\n", emoji, category.getName()));
        sb.append(String.format("💰 **Plafond:** %.2f TND\n", category.getPlafond()));

        if (category.getFields() != null && !category.getFields().isEmpty()) {
            sb.append("\n📋 **Champs requis:**\n");
            for (CategoryRule.CategoryField field : category.getFields()) {
                String required = field.isRequired() ? " (obligatoire)" : " (optionnel)";
                sb.append(String.format("  • **%s**%s", field.getLabel(), required));
                if (field.getDescription() != null) {
                    sb.append(String.format(" - %s", field.getDescription()));
                }
                sb.append("\n");
            }
        }

        if (category.getSpecialRules() != null && !category.getSpecialRules().isEmpty()) {
            sb.append("\n⚠️ **Règles spéciales:**\n");
            for (String rule : category.getSpecialRules()) {
                sb.append(String.format("  • %s\n", rule));
            }
        }

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatResponse buildSpecialRulesResponse(CategoryRule category, ChatRequest request) {
        if (category.getSpecialRules() == null || category.getSpecialRules().isEmpty()) {
            return ChatResponse.builder()
                    .answer(String.format("Aucune règle spéciale pour la catégorie **%s**.", category.getName()))
                    .sessionId(request.getSessionId())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        StringBuilder sb = new StringBuilder();
        String emoji = getCategoryEmoji(category.getName());
        sb.append(String.format("%s **Règles spéciales - %s**\n\n", emoji, category.getName()));

        for (String rule : category.getSpecialRules()) {
            sb.append(String.format("• %s\n", rule));
        }

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
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

    private String getCategoryEmoji(String categoryName) {
        if (categoryName == null) return "📦";
        return switch (categoryName) {
            case "Transport" -> "🚗";
            case "Hébergement" -> "🏨";
            case "Restauration" -> "🍽️";
            case "Carburant" -> "⛽";
            case "Frais professionnels" -> "💼";
            default -> "📦";
        };
    }

    private Long extractNoteId(String question) {
        Pattern pattern = Pattern.compile(".*?(\\d+).*");
        java.util.regex.Matcher matcher = pattern.matcher(question);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractCategoryName(String question) {
        String lowerQuestion = question.toLowerCase();

        // ✅ Pattern pour capturer "plafond de la categorie X" ou "plafond categorie X"
        Pattern pattern = Pattern.compile("plafond\\s+(?:de\\s+)?(?:la\\s+)?(?:cat[eé]gorie\\s+)?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(question);

        if (matcher.find()) {
            String extracted = matcher.group(1);
            log.info("📝 Catégorie extraite de la question: {}", extracted);
            return extracted;
        }

        // ✅ Pattern pour "categorie test" sans "plafond"
        Pattern pattern2 = Pattern.compile("cat[eé]gorie\\s+([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(question);

        if (matcher2.find()) {
            String extracted = matcher2.group(1);
            log.info("📝 Catégorie extraite (sans plafond): {}", extracted);
            return extracted;
        }

        // Fallback: recherche par mots-clés
        for (CategoryRule cat : rulesConfig.getCategories()) {
            if (lowerQuestion.contains(cat.getName().toLowerCase())) {
                return cat.getName();
            }
        }

        // Try partial matches
        Map<String, String> keywords = Map.of(
                "transport", "Transport",
                "hôtel", "Hébergement",
                "hebergement", "Hébergement",
                "hotel", "Hébergement",
                "repas", "Restauration",
                "restaurant", "Restauration",
                "carburant", "Carburant",
                "essence", "Carburant",
                "professionnel", "Frais professionnels",
                "test", "test"  // ✅ Ajout pour "test"
        );

        for (Map.Entry<String, String> entry : keywords.entrySet()) {
            if (lowerQuestion.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String extractComment(String question) {
        // Remove the command part
        String lower = question.toLowerCase();

        // Try to find comment after "valider note 123" or "refuser note 123"
        String[] patterns = {"valider note \\d+", "refuser note \\d+", "rejeter note \\d+"};

        for (String pattern : patterns) {
            String replaced = lower.replaceAll(pattern, "").trim();
            if (!replaced.isEmpty() && !replaced.equals(question.toLowerCase())) {
                return replaced;
            }
        }

        return null;
    }

    private Map<String, LocalDate> extractDateRange(String question) {
        Map<String, LocalDate> result = new HashMap<>();
        Pattern datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
        java.util.regex.Matcher matcher = datePattern.matcher(question);

        List<String> dates = new ArrayList<>();
        while (matcher.find()) {
            dates.add(matcher.group(1));
        }

        if (dates.size() >= 2) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                result.put("start", LocalDate.parse(dates.get(0), formatter));
                result.put("end", LocalDate.parse(dates.get(1), formatter));
            } catch (Exception e) {
                log.error("Error parsing dates", e);
            }
        }

        return result;
    }

    /**
     * Générateur de réponses intelligentes quand l'intention n'est pas trouvée
     */
    private ChatResponse handleUnknownIntent(ChatRequest request) {
        String question = request.getQuestion().toLowerCase();
        String originalQuestion = request.getQuestion();

        log.info("🤔 Intention non trouvée, génération de réponse intelligente pour: {}", originalQuestion);

        // 1. ANALYSER LES MOTS-CLÉS DE LA QUESTION
        Map<String, Object> analysis = analyzeQuestion(question);
        List<String> keywords = (List<String>) analysis.get("keywords");
        boolean hasNoteReference = (boolean) analysis.get("hasNoteReference");
        boolean hasCategoryReference = (boolean) analysis.get("hasCategoryReference");
        boolean hasAmountReference = (boolean) analysis.get("hasAmountReference");
        String possibleIntent = (String) analysis.get("possibleIntent");

        // 2. CONSTRUIRE UNE RÉPONSE COHÉRENTE
        StringBuilder response = new StringBuilder();

        // Si on a une intention probable
        if (possibleIntent != null) {
            response.append("Je vois que vous demandez des informations sur ");

            switch (possibleIntent) {
                case "plafond":
                    response.append("les plafonds. ");
                    response.append("Pour vous aider, pourriez-vous préciser la catégorie (restauration, hébergement, transport) ?\n\n");
                    response.append("Par exemple : 'plafond restauration' ou 'quel est le plafond pour l'hébergement ?'");
                    break;
                case "note":
                    if (hasNoteReference) {
                        response.append("une note spécifique. ");
                        response.append("Pour voir les détails, je peux vous montrer vos notes récentes ou vous pouvez préciser le numéro.\n\n");
                        response.append("Par exemple : 'détails de ma note #92' ou 'montre-moi mes notes'");
                    } else {
                        response.append("vos notes de frais. ");
                        response.append("Voici ce que je peux faire pour vous :\n");
                        response.append("• Voir mes notes : 'mes notes'\n");
                        response.append("• Statut d'une note : 'statut note 123'\n");
                        response.append("• Créer une note : 'créer une note'");
                    }
                    break;
                case "remboursement":
                    response.append("les remboursements. ");
                    response.append("Je peux vous aider avec :\n");
                    response.append("• Total remboursé : 'total remboursé'\n");
                    response.append("• Remboursements partiels : 'comment sont calculés les remboursements partiels ?'");
                    break;
                case "regle":
                    response.append("les règles. ");
                    response.append("Quelles règles vous intéressent ?\n");
                    response.append("• Justificatifs : 'règles justificatifs'\n");
                    response.append("• Workflow : 'workflow de validation'\n");
                    response.append("• Alertes : 'système d'alertes'");
                    break;
                default:
                    response.append("ce sujet. Pourriez-vous reformuler votre question ?");
            }
        }
        // Si on a des mots-clés mais pas d'intention claire
        else if (!keywords.isEmpty()) {
            response.append("J'ai bien reçu votre question sur ");
            response.append(String.join(", ", keywords.subList(0, Math.min(3, keywords.size()))));
            response.append(". ");

            // Proposer des aides contextuelles
            response.append("\n\n💡 **Suggestions :**\n");

            if (hasCategoryReference) {
                response.append("• Voir les plafonds : 'plafonds'\n");
            }
            if (hasNoteReference) {
                response.append("• Voir mes notes : 'mes notes'\n");
            }
            if (hasAmountReference) {
                response.append("• Voir mes totaux : 'total dépenses'\n");
            }

            response.append("• Aide générale : 'aide'");
        }
        // Réponse par défaut élégante
        else {
            response.append("Je n'ai pas bien compris votre demande. ");
            response.append("Je peux vous aider avec :\n\n");
            response.append("📝 **Gestion des notes**\n");
            response.append("• Créer une note : 'créer note'\n");
            response.append("• Voir mes notes : 'mes notes'\n");
            response.append("• Statut d'une note : 'statut note 123'\n\n");

            response.append("💰 **Plafonds et règles**\n");
            response.append("• Plafonds : 'plafond restauration'\n");
            response.append("• Règles : 'règles justificatifs'\n");
            response.append("• Remboursements : 'remboursements partiels'\n\n");

            response.append("❓ **Aide**\n");
            response.append("• Aide générale : 'aide'\n");
            response.append("• FAQ : 'faq'");
        }

        // Ajouter une touche personnelle
        response.append("\n\n😊 Comment puis-je vous aider ?");

        return ChatResponse.builder()
                .answer(response.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(generateContextualQuickReplies(request, keywords))
                .responseType("smart_fallback")
                .build();
    }

    /**
     * Analyse la question pour extraire des informations utiles
     */
    private Map<String, Object> analyzeQuestion(String question) {
        Map<String, Object> result = new HashMap<>();
        List<String> keywords = new ArrayList<>();

        // Mots-clés importants
        Map<String, List<String>> keywordMap = Map.of(
                "note", List.of("note", "notes", "dépense", "frais", "#"),
                "plafond", List.of("plafond", "montant", "limite", "maximum"),
                "categorie", List.of("catégorie", "type", "restauration", "hébergement", "transport", "carburant"),
                "remboursement", List.of("rembours", "paiement", "total", "somme"),
                "regle", List.of("règle", "règles", "validation", "justificatif", "alerte", "workflow"),
                "statut", List.of("statut", "status", "avancement", "où"),
                "aide", List.of("aide", "help", "comment", "bonjour")
        );

        for (Map.Entry<String, List<String>> entry : keywordMap.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (question.contains(keyword)) {
                    keywords.add(entry.getKey());
                    break;
                }
            }
        }

        // Détections spécifiques
        boolean hasNoteReference = question.matches(".*(note|dépense|frais).*#?\\d+.*") ||
                question.contains("#");
        boolean hasCategoryReference = keywordMap.get("categorie").stream()
                .anyMatch(question::contains);
        boolean hasAmountReference = question.matches(".*\\d+\\s*(tnd|dt|euro|eur|usd).*") ||
                question.contains("combien");

        // Déterminer l'intention probable
        String possibleIntent = null;
        if (keywords.contains("plafond") && hasCategoryReference) {
            possibleIntent = "plafond";
        } else if (keywords.contains("note") || hasNoteReference) {
            possibleIntent = "note";
        } else if (keywords.contains("remboursement")) {
            possibleIntent = "remboursement";
        } else if (keywords.contains("regle")) {
            possibleIntent = "regle";
        } else if (keywords.contains("aide")) {
            possibleIntent = "aide";
        }

        result.put("keywords", keywords);
        result.put("hasNoteReference", hasNoteReference);
        result.put("hasCategoryReference", hasCategoryReference);
        result.put("hasAmountReference", hasAmountReference);
        result.put("possibleIntent", possibleIntent);

        return result;
    }

    /**
     * Génère des quick replies contextuelles
     */
    private List<QuickReply> generateContextualQuickReplies(ChatRequest request, List<String> keywords) {
        List<QuickReply> replies = new ArrayList<>();

        if (keywords.contains("plafond")) {
            replies.add(QuickReply.builder()
                    .text("Plafond restauration")
                    .payload("CATEGORY_PLAFOND_RESTAURATION")
                    .icon("🍽️")
                    .build());
            replies.add(QuickReply.builder()
                    .text("Plafond hébergement")
                    .payload("CATEGORY_PLAFOND_HEBERGEMENT")
                    .icon("🏨")
                    .build());
            replies.add(QuickReply.builder()
                    .text("Plafond transport")
                    .payload("CATEGORY_PLAFOND_TRANSPORT")
                    .icon("🚗")
                    .build());
        } else if (keywords.contains("note")) {
            replies.add(QuickReply.builder()
                    .text("Mes notes")
                    .payload("VIEW_NOTES")
                    .icon("📋")
                    .build());
            replies.add(QuickReply.builder()
                    .text("Créer une note")
                    .payload("CREATE_NOTE")
                    .icon("➕")
                    .build());
            replies.add(QuickReply.builder()
                    .text("Statut d'une note")
                    .payload("NOTE_STATUS")
                    .icon("⏳")
                    .build());
        } else {
            // Quick replies par défaut
            return generateQuickReplies(request);
        }

        // Ajouter toujours l'aide
        replies.add(QuickReply.builder()
                .text("❓ Aide")
                .payload("HELP")
                .icon("❓")
                .build());

        return replies;
    }

    /**
     * Génère les quick replies standards
     */
    private List<QuickReply> generateQuickReplies(ChatRequest request) {
        List<QuickReply> replies = new ArrayList<>();

        replies.add(QuickReply.builder()
                .text("Plafonds")
                .payload("CATEGORY_PLAFOND")
                .icon("💰")
                .build());
        replies.add(QuickReply.builder()
                .text("Mes notes")
                .payload("VIEW_NOTES")
                .icon("📋")
                .build());
        replies.add(QuickReply.builder()
                .text("Règles")
                .payload("VALIDATION_RULES")
                .icon("📝")
                .build());
        replies.add(QuickReply.builder()
                .text("❓ Aide")
                .payload("HELP")
                .icon("❓")
                .build());

        return replies;
    }
    /**
     * Récupère le nom d'une catégorie depuis le repository
     */
    private String getCategoryNameFromRepository(Long categoryId) {
        if (categoryId == null) return "Catégorie inconnue";

        // Récupérer le nom depuis le cache ou le repository
        // Note: Vous devez injecter CategoryRepository dans cette classe
        Optional<com.coralio.chatbotmicroservice.entity.Category> category =
                categoryRepository.findById(categoryId);

        return category.map(com.coralio.chatbotmicroservice.entity.Category::getName)
                .orElse("Catégorie " + categoryId);
    }
    /**
     * Construit les détails d'une catégorie depuis la base de données (sans LLM)
     * Format comme le Smart Chatbot
     */
    /**
     * Construit les détails d'une catégorie depuis la base de données (sans LLM)
     * Format comme le Smart Chatbot
     */
    private ChatResponse buildCategoryDetailResponseFromDatabase(Category category, ChatRequest request) {
        StringBuilder sb = new StringBuilder();

        // En-tête comme dans Smart Chatbot
        sb.append("La catégorie est : **").append(category.getName()).append("**\n\n");
        sb.append("Voici les détails :\n");

        // Plafond
        sb.append("* Plafond : ").append(String.format("%.2f", category.getPlafond())).append(" TND\n");

        // Description (si disponible)
        if (category.getDescription() != null && !category.getDescription().isEmpty()) {
            sb.append("* Description : ").append(category.getDescription()).append("\n");
        }

        // ✅ Champs - Utiliser la liste de CategoryField
        List<CategoryField> fields = category.getFields();

        if (fields != null && !fields.isEmpty()) {
            sb.append("* Champs : \n");

            // Séparer les champs obligatoires et optionnels
            List<CategoryField> requiredFields = fields.stream()
                    .filter(CategoryField::isRequired)
                    .toList();
            List<CategoryField> optionalFields = fields.stream()
                    .filter(f -> !f.isRequired())
                    .toList();

            // Champs obligatoires
            for (CategoryField field : requiredFields) {
                sb.append("  - ").append(field.getFieldName())
                        .append(" (").append(field.getFieldType()).append(")")
                        .append(" [OBLIGATOIRE]\n");
            }

            // Champs optionnels
            for (CategoryField field : optionalFields) {
                sb.append("  - ").append(field.getFieldName())
                        .append(" (").append(field.getFieldType()).append(")")
                        .append(" [OPTIONNEL]\n");
            }
        }

        // Conseils pour créer une note
        sb.append("\n💡 Pour créer une note avec cette catégorie, dites : 'créer note ").append(category.getName().toLowerCase()).append("'");

        return ChatResponse.builder()
                .answer(sb.toString())
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .quickReplies(List.of(
                        QuickReply.builder().text("📝 Créer une note").payload("CREATE_NOTE").icon("📝").build(),
                        QuickReply.builder().text("💰 Tous les plafonds").payload("CATEGORY_PLAFOND").icon("💰").build(),
                        QuickReply.builder().text("❓ Aide").payload("HELP").icon("❓").build()
                ))
                .build();
    }
    /**
     * Handle social intents (greetings, gratitude, farewell, positive feedback)
     */
    private ChatResponse handleSocialIntent(String intent, ChatRequest request) {
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

        return ChatResponse.builder()
                .answer(response)
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .responseType("social")
                .quickReplies(generateContextualQuickReplies(request, List.of()))
                .build();
    }
    /**
     * Représente un message de l'historique
     */
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
    /**
     * Récupère l'historique de la conversation depuis la session
     */
    /**
     * Récupère l'historique de la conversation depuis la session
     */
    private List<ChatMessage> getConversationHistory(String sessionToken) {
        List<ChatMessage> history = new ArrayList<>();

        try {
            log.info("🔍 Récupération de l'historique pour session: {}", sessionToken);

            // ✅ Utiliser getSessionHistory qui est déjà testé et fonctionnel
            List<com.coralio.chatbotmicroservice.entity.ChatMessage> messages =
                    sessionService.getSessionHistory(sessionToken, 50);

            log.info("📜 Messages récupérés: {}", messages != null ? messages.size() : 0);

            if (messages != null) {
                for (com.coralio.chatbotmicroservice.entity.ChatMessage msg : messages) {
                    history.add(new ChatMessage(
                            msg.getMessageText(),
                            msg.isUser(),
                            msg.getCreatedAt()
                    ));
                }
                log.info("📜 Historique chargé: {} messages", history.size());
                for (ChatMessage m : history) {
                    log.info("  -> {}: {}", m.isUser() ? "User" : "Bot", m.getContent());
                }
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de la récupération de l'historique: {}", e.getMessage(), e);
        }

        return history;
    }
    /**
     * Extrait le dernier ID de note mentionné dans l'historique
     */
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

    /**
     * Extrait un ID de note d'un texte
     */
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
    // Helper methods to build URLs (no hardcoded localhost)
    private String getUserServiceUrl() {
        return gatewayUrl + "/api/users";
    }

    private String getProjectServiceUrl() {
        return gatewayUrl + "/api/projects";
    }
}