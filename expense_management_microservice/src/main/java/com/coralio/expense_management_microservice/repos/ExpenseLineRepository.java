package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseLineRepository extends JpaRepository<ExpenseLine, Long> {
    List<ExpenseLine> findByExpenseNoteId(Long expenseNoteId);
}
