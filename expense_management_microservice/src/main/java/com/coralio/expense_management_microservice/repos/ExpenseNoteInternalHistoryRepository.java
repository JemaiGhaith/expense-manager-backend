package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseNoteInternalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseNoteInternalHistoryRepository extends JpaRepository<ExpenseNoteInternalHistory, Long> {
    List<ExpenseNoteInternalHistory> findByExpenseNoteIdOrderByCreatedAtAsc(Long expenseNoteId);
}