package com.coralio.expense_management_microservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnInfoDTO {
    private String columnName;
    private String dataType;     // Type SQL (varchar, decimal, etc.)
    private String fieldType;    // Type métier (TEXT, NUMBER, DATE, SELECT, TEXTAREA)
    private boolean hasOptions;  // Si c'est un SELECT avec options
    private String fieldOptions; // Les options JSON pour SELECT
    private Long usageCount;     // Combien de catégories utilisent ce champ
}