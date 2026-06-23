package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.CategoryFieldDTO;
import com.coralio.expense_management_microservice.dto.CategoryRequest;
import com.coralio.expense_management_microservice.entities.Category;
import com.coralio.expense_management_microservice.entities.CategoryField;
import com.coralio.expense_management_microservice.entities.CategoryFieldMapping;
import com.coralio.expense_management_microservice.repos.CategoryFieldMappingRepository;
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
    private final CategoryFieldMappingRepository mappingRepository;
    private final DatabaseMigrationService migrationService;
    private final JdbcTemplate jdbcTemplate;

    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryFieldRepository categoryFieldRepository,
            CategoryFieldMappingRepository mappingRepository,
            DatabaseMigrationService migrationService,
            JdbcTemplate jdbcTemplate) {
        this.categoryRepository = categoryRepository;
        this.categoryFieldRepository = categoryFieldRepository;
        this.mappingRepository = mappingRepository;
        this.migrationService = migrationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CategoryDTO createCategory(CategoryRequest request) {
        try {
            // ✅ 1. VÉRIFICATION: Les champs existants peuvent être réutilisés
            validateFieldNamesForCreation(request.getFields());

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

            // ✅ 4. Ajouter les champs via les mappings
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                int order = 1;
                for (CategoryFieldDTO fieldDTO : request.getFields()) {
                    // Chercher ou créer le champ (définition unique)
                    CategoryField field = categoryFieldRepository
                            .findByFieldName(fieldDTO.getFieldName())
                            .orElseGet(() -> {
                                CategoryField newField = CategoryField.builder()
                                        .fieldName(fieldDTO.getFieldName())
                                        .fieldType(fieldDTO.getFieldType())
                                        .fieldOptions(fieldDTO.getFieldOptions())
                                        .build();
                                return categoryFieldRepository.save(newField);
                            });

                    // Créer le mapping avec les attributs spécifiques à cette catégorie
                    CategoryFieldMapping mapping = CategoryFieldMapping.builder()
                            .category(savedCategory)
                            .field(field)
                            .required(fieldDTO.isRequired())
                            .displayOrder(fieldDTO.getDisplayOrder() != null ? fieldDTO.getDisplayOrder() : order)
                            .build();
                    mappingRepository.save(mapping);
                    order++;
                }
            }

            return convertToDTO(savedCategory);

        } catch (DataIntegrityViolationException e) {
            if (e.getMessage().contains("uk_category_fields_fieldname") ||
                    e.getMessage().contains("unique constraint")) {
                throw new IllegalArgumentException(
                        "❌ Un champ avec ce nom existe déjà. Utilisez-le plutôt que d'en créer un nouveau."
                );
            }
            throw e;
        }
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryRequest request) {
        try {
            // ✅ 1. VÉRIFICATION: Pour la mise à jour, on vérifie uniquement les NOUVEAUX champs
            validateFieldNamesForUpdate(request.getFields(), id);

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

            // ✅ 4. Gestion des mappings
            List<CategoryFieldMapping> existingMappings = mappingRepository.findByCategoryId(id);
            Map<String, CategoryFieldMapping> existingMap = existingMappings.stream()
                    .collect(Collectors.toMap(
                            m -> m.getField().getFieldName(),
                            m -> m
                    ));

            // Nouveaux noms de champs (dans la requête)
            Set<String> newFieldNames = request.getFields().stream()
                    .map(CategoryFieldDTO::getFieldName)
                    .collect(Collectors.toSet());

            // Supprimer les mappings qui ne sont plus dans la requête
            List<CategoryFieldMapping> toRemove = existingMappings.stream()
                    .filter(m -> !newFieldNames.contains(m.getField().getFieldName()))
                    .collect(Collectors.toList());
            if (!toRemove.isEmpty()) {
                mappingRepository.deleteAll(toRemove);
                mappingRepository.flush(); // ⬅️ Force la suppression avant de recharger
            }

            // Ajouter ou mettre à jour les mappings
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                int order = 1;
                for (CategoryFieldDTO fieldDTO : request.getFields()) {
                    // Chercher ou créer le champ (définition)
                    CategoryField field = categoryFieldRepository
                            .findByFieldName(fieldDTO.getFieldName())
                            .orElseGet(() -> {
                                CategoryField newField = CategoryField.builder()
                                        .fieldName(fieldDTO.getFieldName())
                                        .fieldType(fieldDTO.getFieldType())
                                        .fieldOptions(fieldDTO.getFieldOptions())
                                        .build();
                                return categoryFieldRepository.save(newField);
                            });

                    // Vérifier si un mapping existe déjà
                    CategoryFieldMapping mapping = existingMap.get(fieldDTO.getFieldName());
                    if (mapping != null) {
                        // Mettre à jour les attributs
                        mapping.setRequired(fieldDTO.isRequired());
                        mapping.setDisplayOrder(fieldDTO.getDisplayOrder() != null ? fieldDTO.getDisplayOrder() : order);
                        mappingRepository.save(mapping);
                    } else {
                        // Créer un nouveau mapping
                        mapping = CategoryFieldMapping.builder()
                                .category(updatedCategory)
                                .field(field)
                                .required(fieldDTO.isRequired())
                                .displayOrder(fieldDTO.getDisplayOrder() != null ? fieldDTO.getDisplayOrder() : order)
                                .build();
                        mappingRepository.save(mapping);
                    }
                    order++;
                }
            }

            Category refreshedCategory = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée après mise à jour"));

            return convertToDTO(refreshedCategory);

        } catch (DataIntegrityViolationException e) {
            if (e.getMessage().contains("uk_category_fields_fieldname") ||
                    e.getMessage().contains("unique constraint")) {
                throw new IllegalArgumentException(
                        "❌ Conflit de noms de champs. Vérifiez que vous n'essayez pas de créer un champ qui existe déjà."
                );
            }
            throw e;
        }
    }

    /**
     * ✅ VÉRIFICATION POUR CRÉATION: Permet de réutiliser les champs existants
     */
    private void validateFieldNamesForCreation(List<CategoryFieldDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }

        // 1️⃣ Vérifier les doublons DANS LA REQUÊTE
        Set<String> uniqueNames = new HashSet<>();
        for (CategoryFieldDTO field : fields) {
            String fieldName = field.getFieldName();

            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("❌ Le nom d'un champ ne peut pas être vide");
            }

            fieldName = fieldName.trim();
            field.setFieldName(fieldName);

            if (!uniqueNames.add(fieldName)) {
                throw new IllegalArgumentException(
                        "❌ Doublon détecté dans la requête : le champ '" + fieldName + "' apparaît plusieurs fois"
                );
            }
        }

        // ✅ 2️⃣ Pour la CRÉATION: on NE VÉRIFIE PAS l'unicité globale
        // On permet de réutiliser les champs existants
        System.out.println("✅ Validation création: " + fields.size() + " champs (les existants peuvent être réutilisés)");
    }

    /**
     * ✅ VÉRIFICATION POUR MISE À JOUR: Vérifie uniquement les NOUVEAUX champs
     */
    private void validateFieldNamesForUpdate(List<CategoryFieldDTO> fields, Long categoryId) {
        if (fields == null || fields.isEmpty()) {
            return;
        }

        // 1️⃣ Vérifier les doublons DANS LA REQUÊTE
        Set<String> uniqueNames = new HashSet<>();
        for (CategoryFieldDTO field : fields) {
            String fieldName = field.getFieldName();

            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("❌ Le nom d'un champ ne peut pas être vide");
            }

            fieldName = fieldName.trim();
            field.setFieldName(fieldName);

            if (!uniqueNames.add(fieldName)) {
                throw new IllegalArgumentException(
                        "❌ Doublon détecté dans la requête : le champ '" + fieldName + "' apparaît plusieurs fois"
                );
            }
        }

        // 2️⃣ Récupérer les champs existants de CETTE catégorie via les mappings
        List<CategoryFieldMapping> existingMappings = mappingRepository.findByCategoryId(categoryId);
        Set<String> existingFieldNamesInCategory = existingMappings.stream()
                .map(m -> m.getField().getFieldName())
                .collect(Collectors.toSet());

        // 3️⃣ Vérifier UNIQUEMENT les NOUVEAUX champs (qui ne sont pas déjà dans cette catégorie)
        for (CategoryFieldDTO field : fields) {
            String fieldName = field.getFieldName();

            // Si le champ est déjà dans cette catégorie, c'est OK (c'est une mise à jour)
            if (existingFieldNamesInCategory.contains(fieldName)) {
                continue;
            }

            // ✅ Pour les NOUVEAUX champs, on permet la réutilisation des champs existants
            System.out.println("📝 Nouveau champ dans cette catégorie (peut être existant ailleurs): " + fieldName);
        }

        System.out.println("✅ Validation mise à jour: " + fields.size() + " champs");
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

            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec id: " + id));

            System.out.println("📋 Catégorie trouvée: " + category.getName());

            // Supprimer les mappings
            List<CategoryFieldMapping> mappings = mappingRepository.findByCategoryId(id);
            System.out.println("📊 " + mappings.size() + " mappings trouvés");

            if (!mappings.isEmpty()) {
                mappingRepository.deleteAll(mappings);
                mappingRepository.flush();
            }

            categoryRepository.delete(category);
            categoryRepository.flush();

            System.out.println("✅ Catégorie " + id + " supprimée avec succès");

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la suppression: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la suppression de la catégorie: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOUVELLE MÉTHODE: Récupérer tous les champs disponibles (bibliothèque)
     */
    public List<CategoryFieldDTO> getAllAvailableFields() {
        return categoryFieldRepository.findAll().stream()
                .map(this::convertFieldToDTO)
                .collect(Collectors.toList());
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
        // Récupérer les champs à partir des mappings
        List<CategoryFieldDTO> fieldDTOs = category.getFieldMappings().stream()
                .map(mapping -> {
                    CategoryField field = mapping.getField();
                    return CategoryFieldDTO.builder()
                            .id(field.getId())
                            .fieldName(field.getFieldName())
                            .fieldType(field.getFieldType())
                            .fieldOptions(field.getFieldOptions())
                            .required(mapping.isRequired())   // pris du mapping
                            .displayOrder(mapping.getDisplayOrder()) // pris du mapping
                            .build();
                })
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

    private CategoryFieldDTO convertFieldToDTO(CategoryField field) {
        // Pour la bibliothèque, on n'a pas de mapping, donc required et displayOrder ne sont pas disponibles
        return CategoryFieldDTO.builder()
                .id(field.getId())
                .fieldName(field.getFieldName())
                .fieldType(field.getFieldType())
                .fieldOptions(field.getFieldOptions())
                .required(false)  // valeur par défaut
                .displayOrder(0)  // valeur par défaut
                .build();
    }

    // ==================== MÉTHODES UTILITAIRES (conservées) ====================

    /**
     * Récupère le plafond d'une catégorie par son ID
     */
    public Double getPlafondByCategoryId(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .map(Category::getPlafond)
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Récupère le nom d'une catégorie par son ID
     */
    public String getCategoryName(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .map(Category::getName)
                .orElse("Catégorie inconnue");
    }

    /**
     * Récupère les noms des champs dynamiques pour une catégorie (via les mappings)
     */
    public List<String> getCategoryFieldNames(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        String sql = "SELECT cf.field_name " +
                "FROM category_field_mapping cfm " +
                "JOIN category_fields cf ON cfm.field_id = cf.id " +
                "WHERE cfm.category_id = ? " +
                "ORDER BY cfm.display_order";
        return jdbcTemplate.queryForList(sql, String.class, categoryId);
    }

    /**
     * Récupère les champs complets (avec métadonnées) pour une catégorie
     */
    public List<CategoryField> getCategoryFields(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        List<CategoryFieldMapping> mappings = mappingRepository.findByCategoryId(categoryId);
        return mappings.stream()
                .map(CategoryFieldMapping::getField)
                .collect(Collectors.toList());
    }
}