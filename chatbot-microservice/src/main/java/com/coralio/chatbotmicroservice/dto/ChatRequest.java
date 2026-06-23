package com.coralio.chatbotmicroservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "User role is required")
    private String userRole;

    @NotBlank(message = "Question is required")
    @Size(max = 1000, message = "Question cannot exceed 1000 characters")
    private String question;

    private String sessionId;

    @Builder.Default
    private Map<String, Object> context = Map.of();

    private String language;

    private boolean useSmart = true;
}