package com.coralio.chatbotmicroservice.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickReply {
    private String text;
    private String payload;
    private String icon;
    private String description;
}