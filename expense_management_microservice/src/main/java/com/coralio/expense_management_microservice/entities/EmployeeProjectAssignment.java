package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_project_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "project_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 100)
    private String employeeId;   // ID Keycloak (sub)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt = LocalDateTime.now();

    // Pas de champ assignedBy (un seul admin, pas nécessaire)
}