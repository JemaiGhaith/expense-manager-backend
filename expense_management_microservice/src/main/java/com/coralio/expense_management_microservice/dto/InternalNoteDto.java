package com.coralio.expense_management_microservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InternalNoteDto {
    private Long id;
    private Long expenseNoteId;
    private String authorId;
    private String authorName;
    private String authorRole;
    private String content;
    private LocalDateTime createdAt;
}