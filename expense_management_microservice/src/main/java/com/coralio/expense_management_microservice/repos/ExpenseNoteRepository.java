package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseNoteRepository extends JpaRepository<ExpenseNote, Long> {
    List<ExpenseNote> findByEmployeeId(String employeeId);
    List<ExpenseNote> findByStatus(ExpenseStatus status);
    List<ExpenseNote> findByProjectIdIn(List<Long> projectIds);

    // ✅ AJOUTEZ CES MÉTHODES
    List<ExpenseNote> findByProjectId(Long projectId);
    List<ExpenseNote> findByProjectIdAndStatus(Long projectId, ExpenseStatus status);
}