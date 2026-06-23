package com.coralio.chatbotmicroservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    private String status;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "decision_comment")
    private String decisionComment;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "manager_id")
    private String managerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "accord_path")
    private String accordPath;

    @OneToMany(mappedBy = "expenseNoteId", fetch = FetchType.EAGER)
    private List<ExpenseLine> lines;

    public String getStatusEmoji() {
        return switch (status) {
            case "EN_ATTENTE" -> "⏳";
            case "VALIDEE" -> "✅";
            case "REFUSEE" -> "❌";
            case "REMBOURSEE" -> "💰";
            default -> "📝";
        };
    }

    public String getFormattedDate() {
        if (createdAt == null) return "";
        return createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public String getStatusWithEmoji() {
        return getStatusEmoji() + " " + status;
    }

    public boolean isPending() {
        return "EN_ATTENTE".equals(status);
    }

    public boolean isApproved() {
        return "VALIDEE".equals(status) || "REMBOURSEE".equals(status);
    }
}