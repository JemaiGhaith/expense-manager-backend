package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.CategoryFieldDTO;
import com.coralio.expense_management_microservice.dto.CategoryRequest;
import com.coralio.expense_management_microservice.entities.Category;
import com.coralio.expense_management_microservice.entities.CategoryField;
import com.coralio.expense_management_microservice.repos.CategoryFieldRepository;  // ✅ NOUVEAU
import com.coralio.expense_management_microservice.repos.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryFieldRepository categoryFieldRepository;  // ✅ AJOUTER

    // ✅ MODIFIER LE CONSTRUCTEUR
    public CategoryService(CategoryRepository categoryRepository,
                           CategoryFieldRepository categoryFieldRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryFieldRepository = categoryFieldRepository;
    }

    // ==================== CRUD AVEC FIELDS ====================

    @Transactional
    public CategoryDTO createCategory(CategoryRequest request) {
        // 1️⃣ Créer la catégorie
        Category category = Category.builder()
                .name(request.getName())
                .plafond(request.getPlafond())
                .description(request.getDescription())
                .active(request.isActive())
                .build();

        Category savedCategory = categoryRepository.save(category);

        // 2️⃣ Créer les champs associés
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            for (CategoryFieldDTO fieldDTO : request.getFields()) {
                CategoryField field = CategoryField.builder()
                        .fieldName(fieldDTO.getFieldName())        // "depart"
                        .fieldType(fieldDTO.getFieldType())        // "TEXT"
                        .fieldOptions(fieldDTO.getFieldOptions())  // options JSON
                        .required(fieldDTO.isRequired())           // true/false
                        .displayOrder(fieldDTO.getDisplayOrder())  // 1,2,3...
                        .category(savedCategory)                  // 🔗 liaison
                        .build();
                categoryFieldRepository.save(field);  // 💾 INSERT dans category_fields
            }
        }

        return convertToDTO(savedCategory);
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryRequest request) {
        // 1️⃣ Récupérer la catégorie
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec id: " + id));

        // 2️⃣ Mettre à jour la catégorie
        category.setName(request.getName());
        category.setPlafond(request.getPlafond());
        category.setDescription(request.getDescription());
        category.setActive(request.isActive());

        Category updatedCategory = categoryRepository.save(category);

        // 3️⃣ 🗑️ SUPPRIMER les anciens champs
        categoryFieldRepository.deleteByCategoryId(id);

        // 4️⃣ ✨ CRÉER les nouveaux champs
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            for (CategoryFieldDTO fieldDTO : request.getFields()) {
                CategoryField field = CategoryField.builder()
                        .fieldName(fieldDTO.getFieldName())
                        .fieldType(fieldDTO.getFieldType())
                        .fieldOptions(fieldDTO.getFieldOptions())
                        .required(fieldDTO.isRequired())
                        .displayOrder(fieldDTO.getDisplayOrder())
                        .category(updatedCategory)
                        .build();
                categoryFieldRepository.save(field);
            }
        }

        return convertToDTO(updatedCategory);
    }

    @Transactional
    public void deleteCategory(Long id) {
        // 🗑️ TOUJOURS supprimer les champs AVANT la catégorie
        categoryFieldRepository.deleteByCategoryId(id);
        categoryRepository.deleteById(id);
    }

    // ==================== MÉTHODES DE LECTURE ====================

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec id: " + id));
        return convertToDTO(category);
    }

    // ==================== CONVERSION ====================

    public CategoryDTO convertToDTO(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .plafond(category.getPlafond())
                .description(category.getDescription())
                .active(category.isActive())
                .fields(category.getFields().stream()  // ✅ Les champs sont automatiquement chargés !
                        .map(this::convertFieldToDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private CategoryFieldDTO convertFieldToDTO(CategoryField field) {
        return CategoryFieldDTO.builder()
                .id(field.getId())
                .fieldName(field.getFieldName())
                .fieldType(field.getFieldType())
                .fieldOptions(field.getFieldOptions())
                .required(field.isRequired())
                .displayOrder(field.getDisplayOrder())
                .build();
    }
}