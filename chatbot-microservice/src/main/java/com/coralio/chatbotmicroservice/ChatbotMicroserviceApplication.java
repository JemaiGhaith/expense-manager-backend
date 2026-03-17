package com.coralio.chatbotmicroservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.coralio.chatbotmicroservice.repository")
@EntityScan(basePackages = "com.coralio.chatbotmicroservice.entity")
@EnableAsync
@EnableScheduling
public class ChatbotMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotMicroserviceApplication.class, args);

        System.out.println("""
            
            ╔══════════════════════════════════════════════════════════╗
            ║                                                          ║
            ║   🚀 Coral.io Smart Chatbot Microservice                ║
            ║   🔥 Model: llama3.1:8b-instruct-q4_0                                 ║
            ║   📍 Port: 8085                                          ║
            ║   📊 Status: RUNNING                                     ║
            ║                                                          ║
            ╚══════════════════════════════════════════════════════════╝
            """);

        System.out.println("📝 API available at: http://localhost:8085/api/chatbot");
        System.out.println("💬 WebSocket available at: ws://localhost:8085/api/chatbot/ws-chatbot");
    }
}