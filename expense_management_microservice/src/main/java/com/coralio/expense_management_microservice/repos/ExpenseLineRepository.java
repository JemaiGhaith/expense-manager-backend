package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseLineRepository extends JpaRepository<ExpenseLine, Long> {
    List<ExpenseLine> findByExpenseNoteId(Long expenseNoteId);


}
