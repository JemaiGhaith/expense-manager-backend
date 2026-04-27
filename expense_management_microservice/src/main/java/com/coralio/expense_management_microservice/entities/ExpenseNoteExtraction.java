package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "expense_note_extractions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseNoteExtraction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_note_id", nullable = false)
    private Long expenseNoteId;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Map<String, Object> extractedJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "extraction_version")
    private String extractionVersion;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}