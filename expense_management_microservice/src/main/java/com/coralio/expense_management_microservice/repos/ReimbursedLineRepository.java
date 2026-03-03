package com.coralio.expense_management_microservice.repos;

import com.coralio.expense_management_microservice.entities.ReimbursedLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReimbursedLineRepository extends JpaRepository<ReimbursedLine, Long> {

    List<ReimbursedLine> findByPaymentOrderId(Long paymentOrderId);

    List<ReimbursedLine> findByExpenseLineId(Long expenseLineId);

    @Query("SELECT rl FROM ReimbursedLine rl WHERE rl.expenseLine.id IN :lineIds")
    List<ReimbursedLine> findByExpenseLineIds(@Param("lineIds") List<Long> lineIds);

    @Query("SELECT SUM(rl.reimbursedAmount) FROM ReimbursedLine rl WHERE rl.paymentOrder.id = :orderId")
    Double sumReimbursedAmountByOrderId(@Param("orderId") Long orderId);

    boolean existsByExpenseLineId(Long expenseLineId);
}