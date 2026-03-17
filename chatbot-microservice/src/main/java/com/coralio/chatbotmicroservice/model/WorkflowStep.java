package com.coralio.chatbotmicroservice.model;


import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {
    private int step;
    private String name;
    private String description;
    private String role;
}