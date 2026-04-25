package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "expense_notes")
public class ExpenseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "project_id")
    private Long projectId;

    @Enumerated(EnumType.STRING)
    private ExpenseStatus status = ExpenseStatus.EN_ATTENTE;

    @Column(name = "total_amount")
    private Double totalAmount = 0.0;

    private String noteDescription;

    // ✅ NOUVEAU : Commentaire unique (manager OU admin)
    @Column(name = "decision_comment")
    private String decisionComment;

    // ✅ NOUVEAU : Qui a pris la décision (ex: "M:Jean" ou "Admin")
    @Column(name = "decided_by")
    private String decidedBy;

    // ✅ NOUVEAU : Date de la décision
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
    private String managerId;  // ✅ NOUVEAU CHAMP

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "accord_path")
    private String accordPath;

    @Column(name = "ai_analysis_result", columnDefinition = "TEXT")
    private String aiAnalysisResult;

    // Getter et Setter
    public String getAiAnalysisResult() {
        return aiAnalysisResult;
    }

    public void setAiAnalysisResult(String aiAnalysisResult) {
        this.aiAnalysisResult = aiAnalysisResult;
    }
}