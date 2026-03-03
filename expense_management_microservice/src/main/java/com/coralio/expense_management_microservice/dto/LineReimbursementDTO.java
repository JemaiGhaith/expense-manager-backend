package com.coralio.expense_management_microservice.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineReimbursementDTO {
    private Long lineId;
    private Double reimbursedAmount; // null ou 0 pour non remboursé
    private String comment;
    private String categoryName; // Pour l'affichage
    private Double categoryCeiling; // Pour validation côté front
}