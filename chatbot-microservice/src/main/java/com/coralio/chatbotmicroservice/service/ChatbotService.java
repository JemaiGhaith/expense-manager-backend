package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;

public interface ChatbotService {
    ChatResponse processQuestion(ChatRequest request);
    ChatResponse getHelp(String userId, String userRole);
    ChatResponse getContextualHelp(String userId, String userRole, String context);
}