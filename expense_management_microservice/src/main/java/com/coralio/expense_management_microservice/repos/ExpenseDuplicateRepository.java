package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseDuplicate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseDuplicateRepository extends JpaRepository<ExpenseDuplicate, Long> {
    List<ExpenseDuplicate> findByExpenseNoteId(Long expenseNoteId);
    void deleteByExpenseNoteId(Long expenseNoteId);
    boolean existsByExpenseNoteIdAndExpenseLineIdAndUploadedFile(Long expenseNoteId, Long expenseLineId, String uploadedFile);
    boolean existsByExpenseNoteIdAndExpenseLineIdIsNullAndUploadedFile(Long expenseNoteId, String uploadedFile);
    void deleteByExpenseNoteIdAndExpenseLineIdAndUploadedFile(Long noteId, Long lineId, String uploadedFile);
    void deleteByExpenseNoteIdAndExpenseLineIdIsNullAndUploadedFile(Long noteId, String uploadedFile);
}