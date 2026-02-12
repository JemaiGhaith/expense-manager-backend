package com.coralio.expense_management_microservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryFieldDTO {
    private Long id;
    private String fieldName;     // ✅ UN SEUL NOM - "depart", "nombreNuits", etc.
    private String fieldType;     // TEXT, NUMBER, SELECT, DATE, TEXTAREA
    private String fieldOptions;  // Options JSON pour SELECT
    private boolean required;
    private Integer displayOrder;
}