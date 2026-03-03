package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.entities.PaymentMethod;
import com.coralio.expense_management_microservice.entities.PaymentOrderStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaymentOrderDTO {
    private Long id;
    private Long expenseNoteId;
    private String employeeId;
    private String employeeName;
    private Double totalAmount;
    private PaymentOrderStatus status;
    private PaymentMethod paymentMethod;
    private LocalDateTime paymentDate;
    private String paymentReference;
    private String adminComment;
    private LocalDateTime createdAt;
    private List<ReimbursedLineDTO> reimbursedLines;

    @Data
    public static class ReimbursedLineDTO {
        private Long lineId;
        private String description;
        private Double originalAmount;
        private Double reimbursedAmount;
        private Boolean isFullyReimbursed;
        private String categoryName;
        private String adminComment;
    }
}