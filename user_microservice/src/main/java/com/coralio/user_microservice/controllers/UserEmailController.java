package com.coralio.user_microservice.controllers;

import com.coralio.user_microservice.dto.EmailCredentialsDTO;
import com.coralio.user_microservice.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users/email")
@RequiredArgsConstructor
public class UserEmailController {

    private final EmailService emailService;

    @PostMapping("/send-credentials")
    public ResponseEntity<?> sendCredentialsEmail(@RequestBody EmailCredentialsDTO credentials) {
        try {
            emailService.sendWelcomeEmail(credentials);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Email envoyé avec succès");
            response.put("email", credentials.getTo());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Erreur envoi email: " + e.getMessage()));
        }
    }
}