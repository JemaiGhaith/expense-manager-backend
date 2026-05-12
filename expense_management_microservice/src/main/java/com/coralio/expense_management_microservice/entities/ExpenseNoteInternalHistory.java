package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense_note_internal_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseNoteInternalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_note_id", nullable = false)
    private Long expenseNoteId;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_role", nullable = false)
    private String authorRole;   // "MANAGER" ou "ADMIN"

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}