package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.CategoryFieldDTO;
import com.coralio.expense_management_microservice.entities.Category;
import com.coralio.expense_management_microservice.entities.CategoryField;
import com.coralio.expense_management_microservice.repos.CategoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeCategoryService {

    private final CategoryRepository categoryRepository;

    public EmployeeCategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // 🔥 Retourne les catégories AVEC leurs champs (UN SEUL NOM)
    public List<CategoryDTO> getActiveCategoriesForEmployees() {
        return categoryRepository.findByActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private CategoryDTO convertToDTO(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .plafond(category.getPlafond())
                .description(category.getDescription())
                .active(category.isActive())
                .fields(category.getFields().stream()
                        .map(this::convertFieldToDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    // ✅ Conversion directe - UN SEUL NOM
    private CategoryFieldDTO convertFieldToDTO(CategoryField field) {
        return CategoryFieldDTO.builder()
                .id(field.getId())
                .fieldName(field.getFieldName())     // "depart"
                .fieldType(field.getFieldType())     // "TEXT"
                .fieldOptions(field.getFieldOptions()) // null ou JSON
                .required(field.isRequired())        // true
                .displayOrder(field.getDisplayOrder()) // 1
                .build();
    }
}