package com.coralio.chatbotmicroservice.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FAQ {
    private String question;
    private String answer;
}