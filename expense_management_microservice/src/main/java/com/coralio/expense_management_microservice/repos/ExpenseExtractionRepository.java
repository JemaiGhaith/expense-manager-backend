package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ExpenseExtractionRepository extends JpaRepository<ExpenseExtraction, Long> {
    Optional<ExpenseExtraction> findByExpenseLineId(Long expenseLineId);
    void deleteByExpenseLineId(Long expenseLineId);
}