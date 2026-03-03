package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.PaymentOrderDTO;
import com.coralio.expense_management_microservice.entities.PaymentOrder;
import com.coralio.expense_management_microservice.entities.ReimbursedLine;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class PaymentOrderMapperService {

    private final CategoryService categoryService;

    public PaymentOrderMapperService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public PaymentOrderDTO toDto(PaymentOrder order) {
        if (order == null) return null;

        PaymentOrderDTO dto = new PaymentOrderDTO();
        dto.setId(order.getId());
        dto.setExpenseNoteId(order.getExpenseNote().getId());
        dto.setEmployeeId(order.getExpenseNote().getEmployeeId());
        dto.setEmployeeName("Employé " + order.getExpenseNote().getEmployeeId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setStatus(order.getStatus());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setPaymentDate(order.getPaymentDate());
        dto.setPaymentReference(order.getPaymentReference());
        dto.setAdminComment(order.getAdminComment());
        dto.setCreatedAt(order.getCreatedAt());

        if (order.getReimbursedLines() != null) {
            dto.setReimbursedLines(order.getReimbursedLines().stream()
                    .map(this::toReimbursedLineDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private PaymentOrderDTO.ReimbursedLineDTO toReimbursedLineDto(ReimbursedLine line) {
        PaymentOrderDTO.ReimbursedLineDTO dto = new PaymentOrderDTO.ReimbursedLineDTO();
        dto.setLineId(line.getExpenseLine().getId());
        dto.setDescription(line.getExpenseLine().getDescription());
        dto.setOriginalAmount(line.getOriginalAmount());
        dto.setReimbursedAmount(line.getReimbursedAmount());
        dto.setIsFullyReimbursed(line.getIsFullyReimbursed());
        dto.setAdminComment(line.getAdminComment());

        Long categoryId = line.getExpenseLine().getCategoryId();
        dto.setCategoryName(categoryService.getCategoryName(categoryId));

        return dto;
    }
}