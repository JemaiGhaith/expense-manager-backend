package com.coralio.chatbotmicroservice.service;


import org.springframework.stereotype.Service;

@Service
public class ResponseFormatter {

    public String formatResponse(String rawResponse, String intent) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "Je n'ai pas pu générer une réponse. Veuillez reformuler votre question.";
        }

        // Clean up the response
        String formatted = rawResponse.trim();

        // Remove any leftover template markers
        formatted = formatted.replaceAll("<[^>]+>", "");
        formatted = formatted.replaceAll("\\[.*?\\]", "");

        // Add emojis based on intent if not already present
        if (!containsEmoji(formatted)) {
            formatted = addIntentEmoji(formatted, intent);
        }

        // Ensure proper punctuation
        if (!formatted.endsWith(".") && !formatted.endsWith("!") && !formatted.endsWith("?")) {
            formatted += ".";
        }

        return formatted;
    }

    private boolean containsEmoji(String text) {
        return text.codePoints().anyMatch(cp ->
                cp >= 0x1F600 && cp <= 0x1F64F ||  // Emoticons
                        cp >= 0x1F300 && cp <= 0x1F5FF ||  // Symbols
                        cp >= 0x1F680 && cp <= 0x1F6FF ||  // Transport
                        cp >= 0x2600 && cp <= 0x26FF       // Miscellaneous
        );
    }

    private String addIntentEmoji(String text, String intent) {
        String emoji = switch (intent) {
            case "CREATE_NOTE" -> "📝 ";
            case "VIEW_NOTES" -> "📋 ";
            case "NOTE_STATUS" -> "📊 ";
            case "CATEGORY_INFO" -> "💰 ";
            case "DOCUMENT_RULES" -> "📎 ";
            case "VALIDATION_RULES" -> "✅ ";
            case "TOTAL_AMOUNT" -> "💶 ";
            case "MANAGER_ACTIONS" -> "👔 ";
            default -> "🤖 ";
        };

        return emoji + text;
    }
}