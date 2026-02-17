package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.CategoryFieldDTO;
import com.coralio.expense_management_microservice.dto.CategoryRequest;
import com.coralio.expense_management_microservice.entities.Category;
import com.coralio.expense_management_microservice.entities.CategoryField;
import com.coralio.expense_management_microservice.repos.CategoryFieldRepository;
import com.coralio.expense_management_microservice.repos.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryFieldRepository categoryFieldRepository;
    private final DatabaseMigrationService migrationService;
    private final JdbcTemplate jdbcTemplate;

    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryFieldRepository categoryFieldRepository,
            DatabaseMigrationService migrationService,
            JdbcTemplate jdbcTemplate) {
        this.categoryRepository = categoryRepository;
        this.categoryFieldRepository = categoryFieldRepository;
        this.migrationService = migrationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CategoryDTO createCategory(CategoryRequest request) {
        try {
            // ✅ 1. VÉRIFICATION D'UNICITÉ DES fieldName
            validateFieldNamesUniqueness(request.getFields(), null);

            // ✅ 2. Vérifier et créer les colonnes manquantes dans expense_lines
            ensureColumnsExist(request.getFields());

            // ✅ 3. Créer la catégorie
            Category category = Category.builder()
                    .name(request.getName())
                    .plafond(request.getPlafond())
                    .description(request.getDescription())
                    .active(request.isActive())
                    .build();

            Category savedCategory = categoryRepository.save(category);

            // ✅ 4. Ajouter les champs
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                for (CategoryFieldDTO fieldDTO : request.getFields()) {
                    CategoryField field = CategoryField.builder()
                            .fieldName(fieldDTO.getFieldName())
                            .fieldType(fieldDTO.getFieldType())
                            .fieldOptions(fieldDTO.getFieldOptions())
                            .required(fieldDTO.isRequired())
                            .displayOrder(fieldDTO.getDisplayOrder())
                            .category(savedCategory)
                            .build();
                    categoryFieldRepository.save(field);
                }
            }

            return convertToDTO(savedCategory);

        } catch (DataIntegrityViolationException e) {
            // ✅ Capture la violation de contrainte d'unicité
            if (e.getMessage().contains("uk_category_fields_fieldname") ||
                    e.getMessage().contains("unique constraint")) {
                throw new IllegalArgumentException(
                        "❌ Un champ avec ce nom existe déjà dans une autre catégorie. " +
                                "Les noms de champs doivent être uniques dans toute l'application."
                );
            }
            throw e;
        }
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryRequest request) {
        try {
            // ✅ 1. VÉRIFICATION D'UNICITÉ DES fieldName (en excluant cette catégorie)
            validateFieldNamesUniqueness(request.getFields(), id);

            // ✅ 2. Vérifier et créer les colonnes manquantes
            ensureColumnsExist(request.getFields());

            // ✅ 3. Récupérer et mettre à jour la catégorie
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée"));

            category.setName(request.getName());
            category.setPlafond(request.getPlafond());
            category.setDescription(request.getDescription());
            category.setActive(request.isActive());

            Category updatedCategory = categoryRepository.save(category);

            // ✅ 4. Gestion des champs
            List<CategoryField> existingFields = categoryFieldRepository.findByCategoryId(id);
            Map<String, CategoryField> existingFieldsMap = existingFields.stream()
                    .collect(Collectors.toMap(CategoryField::getFieldName, field -> field));

            // Traiter les nouveaux champs
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                for (CategoryFieldDTO fieldDTO : request.getFields()) {
                    CategoryField field;

                    if (existingFieldsMap.containsKey(fieldDTO.getFieldName())) {
                        // MISE À JOUR
                        field = existingFieldsMap.get(fieldDTO.getFieldName());
                        field.setFieldType(fieldDTO.getFieldType());
                        field.setFieldOptions(fieldDTO.getFieldOptions());
                        field.setRequired(fieldDTO.isRequired());
                        field.setDisplayOrder(fieldDTO.getDisplayOrder());
                    } else {
                        // CRÉATION
                        field = CategoryField.builder()
                                .fieldName(fieldDTO.getFieldName())
                                .fieldType(fieldDTO.getFieldType())
                                .fieldOptions(fieldDTO.getFieldOptions())
                                .required(fieldDTO.isRequired())
                                .displayOrder(fieldDTO.getDisplayOrder())
                                .category(updatedCategory)
                                .build();
                    }

                    categoryFieldRepository.save(field);
                    existingFieldsMap.remove(fieldDTO.getFieldName());
                }
            }

            // Supprimer les champs orphelins
            if (!existingFieldsMap.isEmpty()) {
                List<Long> fieldIdsToDelete = existingFieldsMap.values().stream()
                        .map(CategoryField::getId)
                        .collect(Collectors.toList());
                categoryFieldRepository.deleteAllByIdInBatch(fieldIdsToDelete);
            }

            Category refreshedCategory = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée après mise à jour"));

            return convertToDTO(refreshedCategory);

        } catch (DataIntegrityViolationException e) {
            if (e.getMessage().contains("uk_category_fields_fieldname") ||
                    e.getMessage().contains("unique constraint")) {
                throw new IllegalArgumentException(
                        "❌ Un champ avec ce nom existe déjà dans une autre catégorie. " +
                                "Les noms de champs doivent être uniques dans toute l'application."
                );
            }
            throw e;
        }
    }

    /**
     * ✅ VÉRIFICATION D'UNICITÉ DES fieldName
     */
    private void validateFieldNamesUniqueness(List<CategoryFieldDTO> fields, Long categoryIdToExclude) {
        if (fields == null || fields.isEmpty()) {
            return;
        }

        // 1️⃣ Vérifier les doublons DANS LA REQUÊTE
        Set<String> uniqueNames = new HashSet<>();
        for (CategoryFieldDTO field : fields) {
            String fieldName = field.getFieldName();

            // Vérifier null ou vide
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("❌ Le nom d'un champ ne peut pas être vide");
            }

            // Normaliser le nom (trim)
            fieldName = fieldName.trim();
            field.setFieldName(fieldName);

            if (!uniqueNames.add(fieldName)) {
                throw new IllegalArgumentException(
                        "❌ Doublon détecté dans la requête : le champ '" + fieldName + "' apparaît plusieurs fois"
                );
            }
        }

        // 2️⃣ Vérifier les doublons avec la BASE DE DONNÉES
        for (CategoryFieldDTO field : fields) {
            String fieldName = field.getFieldName();

            boolean exists;
            if (categoryIdToExclude != null) {
                // Cas UPDATE : exclure les champs de la catégorie qu'on modifie
                exists = categoryFieldRepository.existsByFieldNameAndCategoryIdNot(fieldName, categoryIdToExclude);
            } else {
                // Cas CREATE : vérifier dans toute la table
                exists = categoryFieldRepository.existsByFieldName(fieldName);
            }

            if (exists) {
                throw new IllegalArgumentException(
                        "❌ Le nom de champ '" + fieldName + "' est déjà utilisé par une autre catégorie.\n" +
                                "Les noms de champs doivent être uniques dans toute l'application.\n" +
                                "Exemples valides : 'depart', 'destination', 'nombreNuits', 'kilometrage'"
                );
            }
        }
    }

    /**
     * ✅ Vérifie et crée les colonnes manquantes dans expense_lines
     */
    private void ensureColumnsExist(List<CategoryFieldDTO> fields) {
        if (fields == null) return;

        // ✅ Récupérer les colonnes EXISTANTES
        List<String> existingColumns = migrationService.getDynamicColumns();

        for (CategoryFieldDTO field : fields) {
            String columnName = field.getFieldName();

            if (!migrationService.isValidColumnName(columnName)) {
                throw new IllegalArgumentException(
                        "❌ Nom de colonne invalide: " + columnName + "\n" +
                                "Utilisez uniquement des lettres, chiffres et underscores, et commencez par une lettre."
                );
            }

            // ✅ Vérifier si la colonne existe DÉJÀ
            if (!existingColumns.contains(columnName)) {
                // ✅ NOUVEAU champ - créer la colonne
                migrationService.addColumn(columnName, field.getFieldType());
                System.out.println("➕ Nouvelle colonne créée: " + columnName);
            } else {
                // ✅ Champ EXISTANT - pas de création !
                System.out.println("♻️ Réutilisation colonne existante: " + columnName);
            }
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        try {
            System.out.println("🗑️ Début suppression catégorie ID: " + id);

            // 1️⃣ Vérifier que la catégorie existe
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec id: " + id));

            System.out.println("📋 Catégorie trouvée: " + category.getName());

            // 2️⃣ Supprimer les champs associés
            List<CategoryField> fields = categoryFieldRepository.findByCategoryId(id);
            System.out.println("📊 " + fields.size() + " champs associés trouvés");

            if (!fields.isEmpty()) {
                // Supprimer tous les champs en une seule fois
                List<Long> fieldIds = fields.stream()
                        .map(CategoryField::getId)
                        .collect(Collectors.toList());
                categoryFieldRepository.deleteAllByIdInBatch(fieldIds);
                categoryFieldRepository.flush();
            }

            // 3️⃣ Maintenant supprimer la catégorie
            categoryRepository.delete(category);
            categoryRepository.flush();

            System.out.println("✅ Catégorie " + id + " supprimée avec succès");

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la suppression: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la suppression de la catégorie: " + e.getMessage(), e);
        }
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
                .fields(category.getFields().stream()
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