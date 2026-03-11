package com.coralio.ai_microservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiMicroserviceApplication {

    public static void main(String[] args) {
        // ✅ Mode headless obligatoire sur serveur sans display
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(AiMicroserviceApplication.class, args);
    }
}
