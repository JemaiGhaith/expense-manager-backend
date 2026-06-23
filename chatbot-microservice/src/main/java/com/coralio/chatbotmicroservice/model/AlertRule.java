package com.coralio.chatbotmicroservice.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {
    private String name;
    private String code;
    private String description;
    private String icon;
    private String color;
    private String severity;
    private String action;
}