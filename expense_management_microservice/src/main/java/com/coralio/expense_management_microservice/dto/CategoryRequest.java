package com.coralio.expense_management_microservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    private String name;
    private Double plafond;
    private String description;
    private boolean active = true;
    private List<CategoryFieldDTO> fields;  // ✅ L'admin envoie fieldName UNIQUE
}