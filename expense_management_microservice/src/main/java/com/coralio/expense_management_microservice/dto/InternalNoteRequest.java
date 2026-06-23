package com.coralio.expense_management_microservice.dto;

import lombok.Data;

@Data
public class InternalNoteRequest {
    private String content;
    private String authorName;
}