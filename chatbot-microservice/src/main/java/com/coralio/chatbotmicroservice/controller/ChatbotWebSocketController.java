package com.coralio.chatbotmicroservice.controller;


import com.coralio.chatbotmicroservice.dto.ChatRequest;
import com.coralio.chatbotmicroservice.dto.ChatResponse;
import com.coralio.chatbotmicroservice.service.ChatbotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class ChatbotWebSocketController {

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public ChatResponse processMessage(@Payload ChatRequest request) {
        log.info("WebSocket message from user {}: {}", request.getUserId(), request.getQuestion());
        return chatbotService.processQuestion(request);
    }

    @MessageMapping("/chat.private")
    public void processPrivateMessage(@Payload ChatRequest request) {
        log.info("Private WebSocket message from user {}: {}", request.getUserId(), request.getQuestion());
        ChatResponse response = chatbotService.processQuestion(request);
        messagingTemplate.convertAndSendToUser(
                request.getUserId(),
                "/queue/messages",
                response
        );
    }
}