package com.coralio.chatbotmicroservice.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class ResponseFormatter {

    private static final Random RANDOM = new Random();

    private static final List<String> INTRODUCTION_PHRASES = Arrays.asList(
            "👋 Bonjour ! Voici ce que je peux vous dire :",
            "😊 Avec plaisir !",
            "✨ Je suis ravi de vous aider !",
            "📌 Voici les informations que vous demandez :",
            "💡 Je vois ce que vous cherchez."
    );

    private static final List<String> CLOSING_PHRASES = Arrays.asList(
            "\n\n💬 **Besoin d'autres informations ?** Je suis là pour vous aider !",
            "\n\n✨ **N'hésitez pas à me poser d'autres questions !**",
            "\n\n📌 **Autre question ?** Je reste à votre disposition.",
            "\n\n😊 **Puis-je faire autre chose pour vous ?**",
            "\n\n🌟 **Je suis à votre écoute pour toute autre question !**"
    );

    private static final Map<String, String> CONTEXTUAL_TIPS = new HashMap<>();

    static {
        CONTEXTUAL_TIPS.put("CREATE_NOTE", "\n\n💡 **Astuce :** Pour créer une note, vous pouvez aussi dire 'créer note transport' directement !");
        CONTEXTUAL_TIPS.put("CATEGORY_PLAFOND", "\n\n💡 **Astuce :** Dites 'plafond restauration' pour plus de détails sur une catégorie spécifique.");
        CONTEXTUAL_TIPS.put("VIEW_NOTES", "\n\n💡 **Astuce :** Pour voir une note en particulier, dites 'détails de la note #92'.");
        CONTEXTUAL_TIPS.put("NOTE_STATUS", "\n\n💡 **Astuce :** Vous pouvez aussi demander 'où en est ma note 123 ?'");
        CONTEXTUAL_TIPS.put("VALIDATION_RULES", "\n\n💡 **Astuce :** Les justificatifs sont essentiels pour un remboursement rapide !");
        CONTEXTUAL_TIPS.put("MANAGER_ACTIONS", "\n\n💡 **Astuce :** Pour valider une note, dites 'valider note 123'.");
        CONTEXTUAL_TIPS.put("HELP", "\n\n💬 **Comment puis-je vous aider aujourd'hui ?**");
    }

    public String formatResponse(String rawResponse, String intent, String userRole) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "🤔 Je n'ai pas bien compris. Pouvez-vous reformuler votre question ?";
        }

        String formatted = rawResponse;

        // 1. Nettoyer les marqueurs techniques
        formatted = cleanTechnicalMarkers(formatted);

        // ✅ NE PAS APPELER formatNumbers() pour garder les nombres bruts
        // formatted = formatNumbers(formatted);  // À COMMENTER

        // 2. Ajouter une introduction seulement si nécessaire
        if (!formatted.startsWith("👋") && !formatted.startsWith("😊") &&
                !formatted.startsWith("✨") && !formatted.startsWith("📌") &&
                !formatted.startsWith("💡") && !formatted.startsWith("📝") &&
                !formatted.startsWith("📋") && !formatted.startsWith("💰") &&
                !formatted.startsWith("⏳") && formatted.length() > 100) {
            formatted = addNaturalIntroduction(formatted, intent);
        }

        // 3. Ajouter des conseils contextuels
        if (!formatted.contains("💡") && formatted.length() < 800) {
            formatted = addContextualTips(formatted, intent);
        }

        // 4. Ajouter une conclusion
        if (!formatted.contains("N'hésitez pas") && !formatted.contains("autre question") &&
                !formatted.contains("Besoin d'autres") && !formatted.contains("Je suis là") &&
                formatted.length() < 800 && !formatted.contains("💬")) {
            formatted = addWarmClosing(formatted, intent);
        }

        // 5. Personnalisation selon le rôle
        formatted = personalizeForRole(formatted, userRole);

        return formatted;
    }

    private String cleanTechnicalMarkers(String text) {
        // Supprimer les marqueurs techniques SANS casser les sauts de ligne
        text = text.replaceAll("SOURCE \\d+", "");
        text = text.replaceAll("\\[SOURCE.*?\\]", "");
        text = text.replaceAll("selon les règles officielles", "");
        text = text.replaceAll("d'après la base de données", "");
        text = text.replaceAll("je vais vous aider", "");
        text = text.replaceAll("je suis une IA", "je suis votre assistant");
        text = text.replaceAll("en tant qu'IA", "");
        text = text.replaceAll("(?i)(voici les informations que vous avez demandées[.,]?\\s*)+", "");

        return text;
    }

    private String formatNumbers(String text) {
        // ✅ Correction : éviter de créer des doubles astérisques
        // Remplacer les nombres avec virgule suivis de TND
        text = text.replaceAll("(\\d+),(\\d+)\\s*TND", "**$1,$2 TND**");

        // Remplacer les nombres sans virgule
        text = text.replaceAll("(\\d+)\\s*TND", "**$1 TND**");

        // Formater les nombres suivis d'unités (personnes, nuits, jours, repas)
        text = text.replaceAll("(\\d+)\\s*(personnes|nuits|jours|repas)", "**$1 $2**");

        // ✅ NETTOYER LES DOUBLES ASTERISQUES QUI POURRAIENT RESTER
        text = text.replaceAll("\\*\\*\\*\\*", "**");
        text = text.replaceAll("\\*\\*\\*", "**");

        return text;
    }

    private String addNaturalIntroduction(String text, String intent) {
        String introduction;

        // Vérifier si le texte commence déjà par un titre
        if (text.startsWith("⏳") || text.startsWith("💰") || text.startsWith("📋") || text.startsWith("📝")) {
            // Le texte a déjà son propre titre, ne pas ajouter d'introduction
            return text;
        }

        if ("CREATE_NOTE".equals(intent)) {
            introduction = "📝 **Pour créer une note de frais :**\n\n";
        } else if ("CATEGORY_PLAFOND".equals(intent)) {
            introduction = "💰 **Voici les informations sur les plafonds :**\n\n";
        } else if ("VIEW_NOTES".equals(intent)) {
            introduction = "📋 **Voici vos notes de frais :**\n\n";
        } else if ("NOTE_STATUS".equals(intent)) {
            introduction = "📊 **Statut de votre note :**\n\n";
        } else if ("FILTER_NOTES_BY_STATUS".equals(intent)) {
            // Ne pas ajouter d'introduction pour les notes filtrées, elles ont déjà leur titre
            return text;
        } else {
            introduction = INTRODUCTION_PHRASES.get(RANDOM.nextInt(INTRODUCTION_PHRASES.size())) + "\n\n";
        }

        return introduction + text;
    }

    private String addContextualTips(String text, String intent) {
        // Ne pas ajouter de tips pour les listes de notes
        if ("FILTER_NOTES_BY_STATUS".equals(intent) || "VIEW_NOTES".equals(intent)) {
            return text;
        }

        if (text.length() > 500) {
            return text;
        }

        String tip = CONTEXTUAL_TIPS.get(intent);
        if (tip != null && !text.contains("Astuce") && !text.contains("💡")) {
            return text + tip;
        }

        return text;
    }

    private String addWarmClosing(String text, String intent) {
        // Ne pas ajouter de conclusion pour les listes de notes
        if ("FILTER_NOTES_BY_STATUS".equals(intent) || "VIEW_NOTES".equals(intent)) {
            return text;
        }

        if ("HELP".equals(intent)) {
            return text + "\n\n💬 **Je suis là pour vous aider !** N'hésitez pas à me demander quoi que ce soit.";
        }

        if ("CATEGORY_PLAFOND".equals(intent)) {
            return text + "\n\n💰 **Besoin d'autres plafonds ?** Dites-moi quelle catégorie vous intéresse !";
        }

        if (RANDOM.nextBoolean()) {
            return text + CLOSING_PHRASES.get(RANDOM.nextInt(CLOSING_PHRASES.size()));
        }

        return text;
    }

    private String personalizeForRole(String text, String userRole) {
        if (text.length() < 100) {
            return text;
        }

        // Ne pas personnaliser si déjà fait
        if (text.contains("en tant qu'employé") || text.contains("👤") || text.contains("👔")) {
            return text;
        }

        if ("EMPLOYEE".equalsIgnoreCase(userRole)) {
            // Ajouter subtilement au début du texte si ce n'est pas déjà fait
            if (text.startsWith("⏳") || text.startsWith("💰") || text.startsWith("📋")) {
                return text;
            }
            if (text.startsWith("👋") || text.startsWith("😊")) {
                return text.replaceFirst("^[^\\n]+", "$0 En tant qu'employé,");
            }
        } else if ("MANAGER".equalsIgnoreCase(userRole)) {
            if (text.contains("notes en attente")) {
                return "👔 **Pour votre département :**\n\n" + text;
            }
        }

        return text;
    }

    public String formatRulesResponse(String rulesText, String userRole) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("📋 **Règles de l'application Coral.io**\n\n");

        String[] sections = rulesText.split("\\n\\n");
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;

            if (section.contains("Plafond") || section.contains("plafond")) {
                formatted.append("💰 ").append(section).append("\n\n");
            } else if (section.contains("Justificatif") || section.contains("justificatif")) {
                formatted.append("📎 ").append(section).append("\n\n");
            } else if (section.contains("Délai") || section.contains("délai")) {
                formatted.append("⏰ ").append(section).append("\n\n");
            } else {
                formatted.append(section).append("\n\n");
            }
        }

        formatted.append("\n💡 **Des questions ?** Je suis là pour vous aider !");
        return formatted.toString();
    }

    public String formatErrorResponse(String errorMessage, String userRole) {
        if (errorMessage.contains("authentifié") || errorMessage.contains("session")) {
            return "🔒 **Session expirée**\n\n" +
                    "Votre session a expiré. Veuillez vous reconnecter pour continuer à utiliser l'assistant.\n\n" +
                    "💡 **Conseil :** Après reconnexion, je pourrai à nouveau vous aider avec vos notes de frais !";
        }

        if (errorMessage.contains("note") && errorMessage.contains("pas")) {
            return "❌ **Note introuvable**\n\n" +
                    "Je n'ai pas trouvé la note que vous cherchez.\n\n" +
                    "💡 **Vérifiez le numéro** ou dites 'mes notes' pour voir la liste de vos notes.";
        }

        return "❌ **Oups !**\n\n" +
                "Je n'ai pas pu traiter votre demande.\n\n" +
                "💡 **Pouvez-vous reformuler ?** Je suis là pour vous aider !";
    }
}