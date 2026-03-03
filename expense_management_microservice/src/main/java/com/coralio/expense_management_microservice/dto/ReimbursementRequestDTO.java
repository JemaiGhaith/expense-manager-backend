package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.entities.PaymentMethod;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class ReimbursementRequestDTO {
    @NotNull(message = "L'ID de la note est requis")
    private Long expenseNoteId;

    @NotNull(message = "La liste des lignes est requise")
    @Size(min = 1, message = "Au moins une ligne doit être traitée")
    private List<LineReimbursementDTO> lines;

    private PaymentMethod paymentMethod;

    private String paymentReference;

    private String adminComment;
}