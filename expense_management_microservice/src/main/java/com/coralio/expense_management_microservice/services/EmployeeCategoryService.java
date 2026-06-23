package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.CategoryFieldDTO;
import com.coralio.expense_management_microservice.entities.Category;
import com.coralio.expense_management_microservice.entities.CategoryField;
import com.coralio.expense_management_microservice.entities.CategoryFieldMapping;
import com.coralio.expense_management_microservice.repos.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeCategoryService {

    private final CategoryRepository categoryRepository;

    public EmployeeCategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // 🔥 Retourne les catégories AVEC leurs champs (via les mappings)
    public List<CategoryDTO> getActiveCategoriesForEmployees() {
        return categoryRepository.findByActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private CategoryDTO convertToDTO(Category category) {
        // Récupérer les champs à partir des mappings
        List<CategoryFieldDTO> fieldDTOs = category.getFieldMappings().stream()
                .map(this::convertMappingToDTO)
                .sorted(Comparator.comparingInt(CategoryFieldDTO::getDisplayOrder))
                .collect(Collectors.toList());

        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .plafond(category.getPlafond())
                .description(category.getDescription())
                .active(category.isActive())
                .fields(fieldDTOs)
                .build();
    }

    // ✅ Conversion à partir du mapping
    private CategoryFieldDTO convertMappingToDTO(CategoryFieldMapping mapping) {
        CategoryField field = mapping.getField();
        return CategoryFieldDTO.builder()
                .id(field.getId())
                .fieldName(field.getFieldName())
                .fieldType(field.getFieldType())
                .fieldOptions(field.getFieldOptions())
                .required(mapping.isRequired())       // depuis le mapping
                .displayOrder(mapping.getDisplayOrder()) // depuis le mapping
                .build();
    }
}