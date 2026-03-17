package com.coralio.chatbotmicroservice.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseNoteDTO {
    private Long id;
    private String employeeId;
    private Long projectId;           // ← AJOUTEZ CE CHAMP !
    private Double totalAmount;
    private String status;
    private String decisionComment;
    private String decidedBy;
    private String managerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime decidedAt;
    private String accordPath;
}