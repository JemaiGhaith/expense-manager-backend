package com.coralio.ai_microservice.mcp;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PromptOptimizer {

    private static final Map<String, String> EXPERT_PROMPTS = new HashMap<>();

    static {
        EXPERT_PROMPTS.put("analyze",
                "Tu es un expert judiciaire en analyse de documents avec 25 ans d'expérience. " +
                        "Tu as travaillé pour Interpol et tu es connu pour ta précision légendaire. " +
                        "Tu ne fais JAMAIS d'erreur et tu ne tolères AUCUNE approximation.\n\n" +

                        "INSTRUCTIONS ABSOLUES:\n" +
                        "1. Examine CHAQUE pixel avec une loupe virtuelle\n" +
                        "2. Si tu as le MOINDRE doute → tu dis 'INCERTAIN'\n" +
                        "3. Ne JAMAIS inventer/compléter une information\n" +
                        "4. Sois 100% certain ou tais-toi\n\n"
        );

        EXPERT_PROMPTS.put("compare",
                "Tu compares DEUX documents avec l'exigence d'un tribunal.\n" +
                        "La moindre différence = NON DOUBLON.\n" +
                        "Tu vérifies:\n" +
                        "- Montants: même chiffres, même virgule, même devise\n" +
                        "- Dates: même jour/mois/année, même format\n" +
                        "- Références: identiques caractère par caractère\n" +
                        "- Visuel: même disposition, mêmes logos\n\n"
        );

        EXPERT_PROMPTS.put("final",
                "DÉCISION FINALE - TU DOIS ÊTRE SÛR À 100%\n" +
                        "Si tu réponds OUI, c'est que tu as vérifié 10 fois.\n" +
                        "Si tu réponds NON, c'est que tu as vu une différence.\n" +
                        "Le doute = NON.\n\n"
        );
    }

    public String buildAnalysisPrompt(String filename) {
        return EXPERT_PROMPTS.get("analyze") +
                "Document à analyser: " + filename + "\n\n" +
                "ANALYSE EXTRÊMEMENT DÉTAILLÉE:\n" +
                "- Type précis de document\n" +
                "- Tous les nombres (montants, quantités, références)\n" +
                "- Toutes les dates\n" +
                "- Tous les noms d'entreprises\n" +
                "- Éléments visuels uniques\n" +
                "- Anomalies ou particularités\n\n" +
                "Résumé au format: RÉSUMÉ|TYPE|MONTANT|DATE|RÉF|FOURNISSEUR\n";
    }

    public String buildComparisonPrompt(String doc1Name, String doc2Name) {
        return EXPERT_PROMPTS.get("compare") +
                "DOCUMENT 1: " + doc1Name + "\n" +
                "DOCUMENT 2: " + doc2Name + "\n\n" +
                "COMPARAISON MÉTICULEUSE:\n\n" +

                "1. MONTANTS:\n" +
                "   - Document 1: ?\n" +
                "   - Document 2: ?\n" +
                "   - Identiques? (oui/non)\n\n" +

                "2. DATES:\n" +
                "   - Document 1: ?\n" +
                "   - Document 2: ?\n" +
                "   - Identiques? (oui/non)\n\n" +

                "3. RÉFÉRENCES:\n" +
                "   - Document 1: ?\n" +
                "   - Document 2: ?\n" +
                "   - Identiques? (oui/non)\n\n" +

                "4. FOURNISSEURS:\n" +
                "   - Document 1: ?\n" +
                "   - Document 2: ?\n" +
                "   - Identiques? (oui/non)\n\n" +

                "5. ANALYSE VISUELLE:\n" +
                "   - Mise en page identique? (oui/non)\n" +
                "   - Logos identiques? (oui/non)\n" +
                "   - Polices identiques? (oui/non)\n\n" +

                "VERDICT FINAL (UNIQUEMENT UN DE CES 3 FORMATS):\n" +
                "DOUBLON|100%|explication\n" +
                "NON_DOUBLON|0%|explication\n" +
                "DOUTE|XX%|explication\n";
    }
}