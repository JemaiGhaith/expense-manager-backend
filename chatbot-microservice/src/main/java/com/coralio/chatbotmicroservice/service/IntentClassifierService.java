package com.coralio.chatbotmicroservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IntentClassifierService {

    // ✅ ADD GREETINGS AND GRATITUDE PATTERNS - FIXED DUPLICATE
    private static final Set<String> GREETING_KEYWORDS = Set.of(
            "bonjour", "salut", "coucou", "hello", "hi", "hey", "bonsoir",
            "bon matin", "bienvenue", "welcome", "yo", "salutation"
    );

    private static final Set<String> GRATITUDE_KEYWORDS = Set.of(
            "merci", "thank", "thanks", "thx", "merci beaucoup", "merci infiniment",
            "je vous remercie", "je te remercie", "thanks a lot", "thank you",
            "c'est gentil", "merci bien"
    );

    private static final Set<String> FAREWELL_KEYWORDS = Set.of(
            "au revoir", "bye", "goodbye", "à bientôt", "à plus",
            "ciao", "adieu", "salut", "à la prochaine", "à tout à l'heure"
    );

    // ✅ FIXED: Remove duplicate "parfait"
    private static final Set<String> POSITIVE_FEEDBACK_KEYWORDS = Set.of(
            "super", "génial", "awesome", "great", "parfait",
            "très bien", "excellent", "cool", "nice",
            "formidable", "magnifique", "top", "impeccable"
    );

    // Patterns pour détection intelligente
    public static final Map<String, Pattern> SIMPLE_PATTERNS = Map.ofEntries(
            // ✅ Add this pattern FIRST (prioritize note details)
            Map.entry("NOTE_DETAILS", Pattern.compile("(?i).*(donner|afficher|voir|lister|montrer|détail|details?|info).*(note|dépense|frais).*#?\\d+.*")),
            Map.entry("CREATE_NOTE", Pattern.compile("(?i).*(cr[ée]er|nouvelle|ajouter).*(note|d[ée]pense|frais).*")),
            Map.entry("VIEW_NOTES", Pattern.compile("(?i).*(voir|afficher|lister|mes).*(notes?).*")),
            Map.entry("FILTER_NOTES_BY_STATUS", Pattern.compile(
                    "(?i).*(mes notes|notes).*(en attente|validées?|refusées?|remboursées?).*"
            )),
            // Pattern pour les RÈGLES
            Map.entry("RULES_QUERY", Pattern.compile(
                    "(?i).*(règle|règles|justificatif|format|taille|workflow|validation|alerte|faq|" +
                            "jours fériés|devise|remboursement partiel|délai|principes|calcul|formule).*")),

            Map.entry("CATEGORY_PLAFOND", Pattern.compile(
                    "(?i).*(plafond|montant|limite|combien).*" +
                            "(restauration|h[ée]bergement|transport|carburant|repas|hôtel|cat[ée]gorie|pour|de).*")),

            Map.entry("MANAGER_ACTIONS", Pattern.compile(
                    "(?i).*(notes? en attente|valider|approuver|refuser|rejeter|en attente de validation).*")),

            Map.entry("NOTE_STATUS", Pattern.compile("(?i).*(statut|o[ùu] en est|avancement).*(note|#\\d+).*")),
            Map.entry("TOTAL_AMOUNT", Pattern.compile("(?i).*(total|somme).*(d[ée]pense|frais).*")),
            Map.entry("HELP", Pattern.compile("(?i).*(aide|help).*"))
    );

    public static final Map<String, Pattern> COMPLEX_PATTERNS = Map.ofEntries(
            Map.entry("ANALYSE_NOTE", Pattern.compile("(?i).*(analyse|évalue|vérifie|conformité).*(note).*")),
            Map.entry("POURQUOI_REFUS", Pattern.compile("(?i).*(pourquoi|raison).*(refus|rejet|refusée).*")),
            Map.entry("COMPARAISON_NOTES", Pattern.compile("(?i).*(compare|différence|versus|vs).*(note).*")),
            Map.entry("CALCUL_COMPLEXE", Pattern.compile("(?i).*(si je|simulation|prévision|combien exactement).*")),
            Map.entry("ANALYSE_DETAILS", Pattern.compile("(?i).*(détail|spécifique|précisément).*(ligne|montant).*"))
    );

    public ClassificationResult classify(String question, String userRole, boolean hasNoteContext) {
        log.info("🔍 Classification de la question: {}", question);

        String lowerQuestion = question.toLowerCase().trim();

        // ✅ PRIORITÉ 0: Détection des salutations (GREETINGS) - HIGHEST PRIORITY
        if (isGreeting(lowerQuestion)) {
            log.info("👋 Détection de salutation");
            return new ClassificationResult("SIMPLE", "GREETING", 0.98);
        }

        // ✅ PRIORITÉ 1: Détection des remerciements (GRATITUDE)
        if (isGratitude(lowerQuestion)) {
            log.info("🙏 Détection de remerciement");
            return new ClassificationResult("SIMPLE", "GRATITUDE", 0.98);
        }

        // ✅ PRIORITÉ 2: Détection des au revoir (FAREWELL)
        if (isFarewell(lowerQuestion)) {
            log.info("👋 Détection d'au revoir");
            return new ClassificationResult("SIMPLE", "FAREWELL", 0.98);
        }

        // ✅ PRIORITÉ 3: Détection des feedbacks positifs (POSITIVE_FEEDBACK)
        if (isPositiveFeedback(lowerQuestion)) {
            log.info("😊 Détection de feedback positif");
            return new ClassificationResult("SIMPLE", "POSITIVE_FEEDBACK", 0.95);
        }

        // ✅ PRIORITÉ 4: Détection des questions sur les catégories et plafonds
        if (isCategoryPlafondQuestion(question)) {
            log.info("✅ Classification: SIMPLE - CATEGORY_PLAFOND");
            return new ClassificationResult("SIMPLE", "CATEGORY_PLAFOND", 0.95);
        }

        // ✅ PRIORITÉ 5: Règles statiques
        if (isStaticRulesQuestion(question)) {
            log.info("✅ Classification: RULES_STATIC (question sur les règles)");
            return new ClassificationResult("RULES", "STATIC_RULES", 1.0);
        }

        // ✅ PRIORITÉ 6: Questions sur les notes personnelles
        if (lowerQuestion.contains("mes notes") || lowerQuestion.contains("ma note")) {
            log.info("✅ Classification: SIMPLE - VIEW_NOTES");
            return new ClassificationResult("SIMPLE", "VIEW_NOTES", 0.95);
        }

        // ✅ PRIORITÉ 7: Détection pour "devise"
        if (lowerQuestion.contains("devise") || lowerQuestion.contains("conversion") ||
                lowerQuestion.contains("euro") || lowerQuestion.contains("dollar") ||
                lowerQuestion.contains("usd") || lowerQuestion.contains("eur")) {
            log.info("✅ Classification: SIMPLE - CURRENCY");
            return new ClassificationResult("SIMPLE", "CURRENCY", 0.95);
        }

        // 8. Patterns SIMPLE
        for (Map.Entry<String, Pattern> entry : SIMPLE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).matches()) {
                log.info("✅ Pattern SIMPLE détecté: {} → confiance 0.9", entry.getKey());
                return new ClassificationResult("SIMPLE", entry.getKey(), 0.9);
            }
        }

        // 9. Patterns COMPLEXES
        for (Map.Entry<String, Pattern> entry : COMPLEX_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).matches()) {
                log.info("✅ Pattern COMPLEXE détecté: {} → confiance 0.9", entry.getKey());
                return new ClassificationResult("COMPLEX", entry.getKey(), 0.9);
            }
        }

        // 10. Analyse par mots-clés pour les règles (fallback)
        if (lowerQuestion.contains("remboursement") && (lowerQuestion.contains("calcul") || lowerQuestion.contains("partiel"))) {
            log.info("📊 Règle détectée: remboursement partiel");
            return new ClassificationResult("RULES", "STATIC_RULES", 0.95);
        }

        if (lowerQuestion.contains("délai") || lowerQuestion.contains("temps")) {
            log.info("📊 Règle détectée: délai remboursement");
            return new ClassificationResult("RULES", "STATIC_RULES", 0.95);
        }

        if (lowerQuestion.contains("justificatif")) {
            log.info("📊 Règle détectée: justificatifs");
            return new ClassificationResult("RULES", "STATIC_RULES", 0.95);
        }

        if (lowerQuestion.contains("alerte")) {
            log.info("📊 Règle détectée: alertes");
            return new ClassificationResult("RULES", "STATIC_RULES", 0.95);
        }

        if (lowerQuestion.contains("workflow") || lowerQuestion.contains("validation")) {
            log.info("📊 Règle détectée: workflow");
            return new ClassificationResult("RULES", "STATIC_RULES", 0.95);
        }

        // 11. Analyse par longueur et complexité
        double complexityScore = calculateComplexity(question);

        if (complexityScore > 0.7) {
            log.info("📊 Question complexe (score: {})", complexityScore);
            return new ClassificationResult("COMPLEX", "GENERAL_COMPLEX", complexityScore);
        }

        // 12. Par défaut
        log.info("📊 Question simple par défaut (score: {})", complexityScore);
        return new ClassificationResult("SIMPLE", "GENERAL_SIMPLE", 0.5);
    }

    // ✅ ADD THESE HELPER METHODS

    private boolean isGreeting(String question) {
        return GREETING_KEYWORDS.stream()
                .anyMatch(keyword -> question.contains(keyword));
    }

    private boolean isGratitude(String question) {
        return GRATITUDE_KEYWORDS.stream()
                .anyMatch(keyword -> question.contains(keyword));
    }

    private boolean isFarewell(String question) {
        return FAREWELL_KEYWORDS.stream()
                .anyMatch(keyword -> question.contains(keyword));
    }

    private boolean isPositiveFeedback(String question) {
        return POSITIVE_FEEDBACK_KEYWORDS.stream()
                .anyMatch(keyword -> question.contains(keyword));
    }

    /**
     * Détecte si c'est une question sur les règles statiques
     */
    private boolean isStaticRulesQuestion(String question) {
        String lower = question.toLowerCase();

        // ✅ Exclure les questions sur les plafonds
        boolean isCategoryPlafond = (lower.contains("plafond") || lower.contains("montant max") || lower.contains("limite")) &&
                (lower.contains("restauration") ||
                        lower.contains("hébergement") || lower.contains("hebergement") ||
                        lower.contains("transport") ||
                        lower.contains("carburant") ||
                        lower.contains("repas") ||
                        lower.contains("hôtel") || lower.contains("hotel") ||
                        lower.contains("catégorie") || lower.contains("categorie") ||
                        lower.contains("category"));

        if (isCategoryPlafond) {
            log.info("🔍 Question sur plafond de catégorie → pas une règle statique");
            return false;
        }

        // Mots-clés des règles statiques
        List<String> staticKeywords = Arrays.asList(
                "règle", "règles", "principes", "principes généraux",
                "regle", "regles",
                "justificatif", "justificatifs", "format", "taille",
                "pdf", "jpg", "png", "doc", "docx", "10 mo",
                "workflow", "validation", "étapes", "brouillon", "soumise",
                "validée", "refusée", "contrôlée", "remboursée",
                "alerte", "alertes", "facture en double", "dépassement plafond",
                "justificatif illisible", "date incohérente",
                "remboursement partiel", "remboursements partiels", "calcul", "calculés",
                "formule", "min", "dépassement",
                "faq", "délai", "temps", "combien de temps", "sans justificatif",
                "jour férié", "jours fériés", "devise", "conversion"
        );

        boolean hasKeyword = staticKeywords.stream().anyMatch(lower::contains);
        boolean isPersonalNote = lower.contains("ma note") ||
                lower.contains("mes notes") ||
                lower.contains("mon remboursement") ||
                lower.matches(".*#\\d+.*");

        log.info("🔍 isStaticRulesQuestion: hasKeyword={}, isPersonalNote={}, isCategoryPlafond={}",
                hasKeyword, isPersonalNote, isCategoryPlafond);

        return hasKeyword && !isPersonalNote && !isCategoryPlafond;
    }

    /**
     * Détecte si c'est une question sur les catégories et plafonds
     */
    private boolean isCategoryPlafondQuestion(String question) {
        String lower = question.toLowerCase();

        // ✅ Détection pour "plafond" seul
        if (lower.equals("plafond") || lower.equals("plafonds")) {
            log.info("🔍 isCategoryPlafondQuestion: true (plafond seul)");
            return true;
        }

        // ✅ Détection pour "Quels sont les plafonds ?" et variantes
        if ((lower.contains("quels") || lower.contains("quelles")) &&
                (lower.contains("plafond") || lower.contains("plafonds"))) {
            log.info("🔍 isCategoryPlafondQuestion: true (question sur les plafonds)");
            return true;
        }

        // ✅ Détection pour "tous les plafonds"
        if (lower.contains("tous") && lower.contains("plafond")) {
            log.info("🔍 isCategoryPlafondQuestion: true (tous les plafonds)");
            return true;
        }

        boolean hasPlafond = lower.contains("plafond") ||
                lower.contains("montant max") ||
                lower.contains("limite") ||
                lower.contains("combien");

        boolean hasCategory = lower.contains("restauration") ||
                lower.contains("hébergement") ||
                lower.contains("hebergement") ||
                lower.contains("transport") ||
                lower.contains("carburant") ||
                lower.contains("repas") ||
                lower.contains("hôtel") ||
                lower.contains("hotel") ||
                lower.contains("catégorie") ||
                lower.contains("categorie") ||
                lower.contains("category") ||
                lower.contains("test");

        boolean isSimplePlafond = lower.matches("plafond\\s+restauration") ||
                lower.matches("plafond\\s+hébergement") ||
                lower.matches("plafond\\s+transport") ||
                lower.matches("plafond\\s+carburant") ||
                lower.matches("plafond\\s+hebergement") ||
                lower.matches("plafond\\s+category.*") ||
                lower.matches("plafond\\s+test.*");

        boolean result = (hasPlafond && hasCategory) || isSimplePlafond ||
                lower.equals("plafond") || lower.equals("plafonds") ||
                (lower.contains("quels") && lower.contains("plafond"));

        if (result) {
            log.info("🔍 isCategoryPlafondQuestion: true");
        }

        return result;
    }

    private double calculateComplexity(String question) {
        double score = 0.0;
        String lower = question.toLowerCase();

        if (question.length() > 100) score += 0.3;
        if (countWords(question) > 15) score += 0.2;

        List<String> complexKeywords = Arrays.asList(
                "pourquoi", "comment expliquer", "analyse", "compare", "différence",
                "calcul", "exactement", "précisément", "détail", "spécifique",
                "conformité", "vérifie", "simulation", "prévision"
        );

        for (String keyword : complexKeywords) {
            if (lower.contains(keyword)) {
                score += 0.15;
                break;
            }
        }

        boolean hasNote = lower.contains("note") || lower.contains("#");
        boolean hasAmount = lower.matches(".*\\d+.*");
        boolean hasCategory = lower.contains("catégorie") || lower.contains("restauration");

        if (hasNote && hasAmount && hasCategory) {
            score += 0.25;
        }

        return Math.min(score, 1.0);
    }

    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    public static class ClassificationResult {
        public final String type;
        public final String intent;
        public final double confidence;

        public ClassificationResult(String type, String intent, double confidence) {
            this.type = type;
            this.intent = intent;
            this.confidence = confidence;
        }

        public boolean isSimple() {
            return "SIMPLE".equals(type);
        }

        public boolean isComplex() {
            return "COMPLEX".equals(type);
        }

        public boolean isRules() {
            return "RULES".equals(type);
        }
    }
}