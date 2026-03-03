package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.PaymentOrder;
import com.coralio.expense_management_microservice.entities.PaymentOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    List<PaymentOrder> findByStatus(PaymentOrderStatus status);

    @Query("SELECT po FROM PaymentOrder po WHERE po.status = :status ORDER BY po.createdAt ASC")
    List<PaymentOrder> findByStatusOrderByCreatedAtAsc(@Param("status") PaymentOrderStatus status);

    Optional<PaymentOrder> findByExpenseNoteId(Long expenseNoteId);

    boolean existsByExpenseNoteId(Long expenseNoteId);

    @Query("SELECT po FROM PaymentOrder po WHERE po.expenseNote.employeeId = :employeeId")
    List<PaymentOrder> findByEmployeeId(@Param("employeeId") String employeeId);

    @Query("SELECT po FROM PaymentOrder po WHERE po.paymentDate BETWEEN :startDate AND :endDate")
    List<PaymentOrder> findByPaymentDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(po) FROM PaymentOrder po WHERE po.status = :status")
    long countByStatus(@Param("status") PaymentOrderStatus status);
}