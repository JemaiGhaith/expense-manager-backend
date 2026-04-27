package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ExpenseLineRepository extends JpaRepository<ExpenseLine, Long> {
    // ✅ Version JPA standard (ne récupère que les champs mappés)
    List<ExpenseLine> findByExpenseNoteId(Long expenseNoteId);

    // ✅ Version NATIVE - récupère TOUTES les colonnes !
    @Query(value = "SELECT * FROM expense_lines WHERE expense_note_id = :noteId",
            nativeQuery = true)
    List<Map<String, Object>> findByExpenseNoteIdNative(@Param("noteId") Long noteId);

    // ✅ Version avec mapping manuel
    @Query(value = "SELECT * FROM expense_lines WHERE expense_note_id = :noteId",
            nativeQuery = true)
    List<ExpenseLine> findByExpenseNoteIdWithAllColumns(@Param("noteId") Long noteId);
    void deleteByExpenseNoteId(Long expenseNoteId);
    @Query("SELECT el FROM ExpenseLine el WHERE el.justificatifPath LIKE :fileName")
    List<ExpenseLine> findByJustificatifPathLike(@Param("fileName") String fileName);
    @Query("SELECT el FROM ExpenseLine el WHERE el.justificatifPath LIKE CONCAT('%', :fileName)")
    List<ExpenseLine> findByJustificatifPathEndingWith(@Param("fileName") String fileName);
}
