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
    private String employeeId; // L'employé qui crée la note (Keycloak ID)

    @Column(name = "project_id")
    private Long projectId;

    @Enumerated(EnumType.STRING)
    private ExpenseStatus status = ExpenseStatus.EN_ATTENTE;

    @Column(name = "total_amount")
    private Double totalAmount = 0.0;

    @Column(name = "manager_comment")
    private String managerComment;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ✅ NOUVEAU : Justificatif d'accord pour la note de frais
    @Column(name = "accord_path")
    private String accordPath;
}
