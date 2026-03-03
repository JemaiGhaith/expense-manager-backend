package com.coralio.expense_management_microservice.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ReimbursementSummaryDTO {
    private Long expenseNoteId;
    private String employeeId;
    private Double totalOriginalAmount;
    private Double totalReimbursedAmount;
    private Map<Long, LineSummary> linesSummary;
    private String warning; // Pour les dépassements de plafond

    @Data
    @Builder
    public static class LineSummary {
        private Long lineId;
        private String description;
        private Double originalAmount;
        private Double reimbursedAmount;
        private Double categoryCeiling;
        private Boolean exceedsCeiling;
        private String reimbursementType; // "TOTAL", "PARTIEL", "NON_REMBOURSE"
    }
}