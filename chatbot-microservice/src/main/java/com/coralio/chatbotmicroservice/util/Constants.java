package com.coralio.chatbotmicroservice.util;


import java.util.List;
import java.util.Map;

public class Constants {

    public static final String GENERAL_RULES = """
        Principes généraux:
        - Remboursement sur justificatifs originaux
        - Délai de traitement : 5 jours ouvrés
        - Conservation des justificatifs : 6 mois
        - Devise : TND (conversion possible en EUR, USD, GBP)
        """;

    public static final String DOCUMENT_RULES = """
        Règles justificatifs:
        - Formats acceptés : PDF, JPG, PNG, DOC, DOCX
        - Taille max : 10 Mo par fichier
        - Un justificatif par ligne de frais
        - Justificatifs illisibles → ALERTE
        """;

    public static final String ALERT_RULES = """
        Alertes automatiques:
        - Dépassement plafond: remboursement partiel automatique
        - Facture en double: vérification manuelle
        - Justificatif illisible: re-scan requis
        - Date incohérente: justificatif requis
        """;

    public static final String HOLIDAY_RULES = """
        Jours non autorisés:
        - Week-ends (samedi, dimanche)
        - Jours fériés tunisiens:
          1er janvier (Nouvel an)
          14 janvier (Fête de la Révolution)
          20 mars (Fête de l'Indépendance)
          9 avril (Fête des Martyrs)
          1er mai (Fête du Travail)
          25 juillet (Fête de la République)
          13 août (Fête de la Femme)
          15 octobre (Fête de l'Évacuation)
          17 décembre (Fête de la Révolution)
        """;

    public static final String WORKFLOW_RULES = """
        Workflow de validation:
        1. Brouillon - Saisie par l'employé
        2. Soumise - En attente validation manager
        3. Validée/Refusée - Décision du manager
        4. Contrôlée - Vérification admin
        5. Remboursée - Paiement effectué
        """;

    public static final List<String> TUNISIAN_HOLIDAYS = List.of(
            "01-01", "14-01", "20-03", "09-04", "01-05",
            "25-07", "13-08", "15-10", "17-12"
    );

    public static final Map<String, String> STATUS_EMOJIS = Map.of(
            "EN_ATTENTE", "⏳",
            "VALIDEE", "✅",
            "REFUSEE", "❌",
            "REMBOURSEE", "💰"
    );

    public static final Map<String, String> CATEGORY_ICONS = Map.of(
            "Transport", "🚗",
            "Hébergement", "🏨",
            "Restauration", "🍽️",
            "Carburant", "⛽",
            "Frais professionnels", "💼",
            "Autres", "📦"
    );
}