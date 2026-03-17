package com.coralio.chatbotmicroservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private String sessionId;
    private LocalDateTime timestamp;
    private List<ActionSuggestion> suggestions;
    private boolean requiresAction;
    private String actionType;
    private Map<String, Object> actionData;
    private List<QuickReply> quickReplies;
    private String responseType; // "smart" or "simple"
    private long processingTimeMs;
}