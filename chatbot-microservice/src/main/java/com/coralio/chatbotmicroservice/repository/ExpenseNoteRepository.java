package com.coralio.chatbotmicroservice.repository;


import com.coralio.chatbotmicroservice.entity.ExpenseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExpenseNoteRepository extends JpaRepository<ExpenseNote, Long> {

    List<ExpenseNote> findByEmployeeId(String employeeId);

    List<ExpenseNote> findByStatus(String status);

    @Query("SELECT e FROM ExpenseNote e WHERE e.employeeId = :employeeId ORDER BY e.createdAt DESC")
    List<ExpenseNote> findRecentByEmployee(@Param("employeeId") String employeeId);

    @Query("SELECT e FROM ExpenseNote e WHERE e.employeeId = :employeeId AND e.createdAt BETWEEN :start AND :end")
    List<ExpenseNote> findByEmployeeAndDateRange(
            @Param("employeeId") String employeeId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT SUM(e.totalAmount) FROM ExpenseNote e WHERE e.employeeId = :employeeId AND e.status IN ('VALIDEE', 'REMBOURSEE')")
    Double getTotalReimbursedForEmployee(@Param("employeeId") String employeeId);

    @Query("SELECT e FROM ExpenseNote e WHERE e.status = :status ORDER BY e.createdAt DESC")
    List<ExpenseNote> findPendingByStatus(@Param("status") String status);

    @Query("SELECT COUNT(e) FROM ExpenseNote e WHERE e.employeeId = :employeeId AND e.status = 'EN_ATTENTE'")
    long countPendingForEmployee(@Param("employeeId") String employeeId);
}