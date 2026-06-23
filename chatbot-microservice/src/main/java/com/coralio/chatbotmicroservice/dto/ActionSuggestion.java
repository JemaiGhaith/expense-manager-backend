package com.coralio.chatbotmicroservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionSuggestion {
    private String type;
    private String label;
    private String description;
    private Map<String, Object> parameters;
    private String confirmationMessage;
}