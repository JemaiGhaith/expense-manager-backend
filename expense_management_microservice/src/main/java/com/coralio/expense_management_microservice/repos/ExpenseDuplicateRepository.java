package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseDuplicate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseDuplicateRepository extends JpaRepository<ExpenseDuplicate, Long> {
    List<ExpenseDuplicate> findByExpenseNoteId(Long expenseNoteId);
    void deleteByExpenseNoteId(Long expenseNoteId);
}