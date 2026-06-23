package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "expense_duplicates")
@Data
public class ExpenseDuplicate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_note_id")
    private Long expenseNoteId;

    @Column(name = "expense_line_id")
    private Long expenseLineId;

    @Column(name = "uploaded_file")
    private String uploadedFile;

    @Column(name = "duplicate_file")
    private String duplicateFile;

    @Column(name = "similarity")
    private Double similarity;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "employee_id")
    private Long employeeId;

    @PrePersist
    public void prePersist() {
        if (detectedAt == null) detectedAt = LocalDateTime.now();
    }
}