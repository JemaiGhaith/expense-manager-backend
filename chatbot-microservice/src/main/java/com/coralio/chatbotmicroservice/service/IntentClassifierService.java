package com.coralio.chatbotmicroservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IntentClassifierService {

    // Patterns pour détection intelligente - VERSION TRÈS PERMISSIVE
    public  static final Map<String, Pattern> SIMPLE_PATTERNS = Map.ofEntries(
            Map.entry("CREATE_NOTE", Pattern.compile("(?i).*(cr[ée]er|nouvelle|ajouter).*(note|d[ée]pense|frais).*")),
            Map.entry("VIEW_NOTES", Pattern.compile("(?i).*(voir|afficher|lister|mes).*(notes?).*")),
            // Pattern TRÈS permissif pour les plafonds
            Map.entry("CATEGORY_PLAFOND", Pattern.compile(
                    "(?i).*(plafond|montant|limite|combien).*" +
                            "(restauration|h[ée]bergement|transport|carburant|repas|hôtel|cat[ée]gorie|pour|de).*")),
            // ✅ NOUVEAU : Pattern pour les actions manager
            Map.entry("MANAGER_ACTIONS", Pattern.compile(
                    "(?i).*(notes? en attente|valider|approuver|refuser|rejeter|en attente de validation|en attente).*")),
            Map.entry("NOTE_STATUS", Pattern.compile("(?i).*(statut|o[ùu] en est|avancement).*(note|#\\d+).*")),
            Map.entry("TOTAL_AMOUNT", Pattern.compile("(?i).*(total|somme).*(d[ée]pense|frais).*")),
            Map.entry("HELP", Pattern.compile("(?i).*(aide|help|bonjour|salut).*"))
    );

    public  static final Map<String, Pattern> COMPLEX_PATTERNS = Map.ofEntries(
            Map.entry("ANALYSE_NOTE", Pattern.compile("(?i).*(analyse|évalue|vérifie|conformité).*(note).*")),
            Map.entry("POURQUOI_REFUS", Pattern.compile("(?i).*(pourquoi|raison).*(refus|rejet|refusée).*")),
            Map.entry("COMPARAISON_NOTES", Pattern.compile("(?i).*(compare|différence|versus|vs).*(note).*")),
            Map.entry("CALCUL_COMPLEXE", Pattern.compile("(?i).*(si je|simulation|prévision|combien exactement).*")),
            Map.entry("ANALYSE_DETAILS", Pattern.compile("(?i).*(détail|spécifique|précisément).*(ligne|montant).*"))
    );

    public ClassificationResult classify(String question, String userRole, boolean hasNoteContext) {
        log.info("🔍 Classification de la question: {}", question);

        String lowerQuestion = question.toLowerCase();

        // 1. Vérifier si c'est une question SIMPLE (pattern matching)
        for (Map.Entry<String, Pattern> entry : SIMPLE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).matches()) {
                log.info("✅ Pattern SIMPLE détecté: {} → confiance 0.9", entry.getKey());
                return new ClassificationResult("SIMPLE", entry.getKey(), 0.9);
            }
        }

        // 2. Vérifier si c'est une question COMPLEXE (pattern matching)
        for (Map.Entry<String, Pattern> entry : COMPLEX_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).matches()) {
                log.info("✅ Pattern COMPLEXE détecté: {} → confiance 0.9", entry.getKey());
                return new ClassificationResult("COMPLEX", entry.getKey(), 0.9);
            }
        }

        // 3. Analyse par mots-clés (fallback)
        if (lowerQuestion.contains("plafond") || lowerQuestion.contains("montant")) {
            if (lowerQuestion.contains("restauration") || lowerQuestion.contains("repas")) {
                log.info("📊 Mots-clés plafond+restauration détectés → SIMPLE avec confiance 0.8");
                return new ClassificationResult("SIMPLE", "CATEGORY_PLAFOND", 0.8);
            }
            if (lowerQuestion.contains("hébergement") || lowerQuestion.contains("hôtel") || lowerQuestion.contains("nuit")) {
                log.info("📊 Mots-clés plafond+hébergement détectés → SIMPLE avec confiance 0.8");
                return new ClassificationResult("SIMPLE", "CATEGORY_PLAFOND", 0.8);
            }
            if (lowerQuestion.contains("transport") || lowerQuestion.contains("voiture") || lowerQuestion.contains("trajet")) {
                log.info("📊 Mots-clés plafond+transport détectés → SIMPLE avec confiance 0.8");
                return new ClassificationResult("SIMPLE", "CATEGORY_PLAFOND", 0.8);
            }
        }

        // 4. Analyse par longueur et complexité
        double complexityScore = calculateComplexity(question);

        if (complexityScore > 0.7) {
            log.info("📊 Question complexe (score: {})", complexityScore);
            return new ClassificationResult("COMPLEX", "GENERAL_COMPLEX", complexityScore);
        }

        // 5. Par défaut, simple
        log.info("📊 Question simple par défaut (score: {})", complexityScore);
        return new ClassificationResult("SIMPLE", "GENERAL_SIMPLE", 0.5);
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

        public boolean isSimple() { return "SIMPLE".equals(type); }
        public boolean isComplex() { return "COMPLEX".equals(type); }
    }
}