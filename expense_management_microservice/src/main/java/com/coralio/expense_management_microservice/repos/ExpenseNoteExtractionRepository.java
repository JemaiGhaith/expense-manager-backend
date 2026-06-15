package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseNoteExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExpenseNoteExtractionRepository extends JpaRepository<ExpenseNoteExtraction, Long> {
    Optional<ExpenseNoteExtraction> findByExpenseNoteId(Long expenseNoteId);
    void deleteByExpenseNoteId(Long expenseNoteId);
}