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
            // ✅ 1. VÉRIFICATION: Les champs existants peuvent être réutilisés
            // Seuls les NOUVEAUX champs (qui n'existent pas dans category_fields) doivent être uniques
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

            // ✅ 4. Ajouter les champs
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                for (CategoryFieldDTO fieldDTO : request.getFields()) {
                    // ✅ Vérifier si le champ existe déjà dans category_fields
                    Optional<CategoryField> existingField = categoryFieldRepository.findByFieldName(fieldDTO.getFieldName());

                    CategoryField field;
                    if (existingField.isPresent()) {
                        // ✅ RÉUTILISER le champ existant
                        field = existingField.get();
                        // Mettre à jour les métadonnées pour cette catégorie
                        field.setFieldType(fieldDTO.getFieldType());
                        field.setFieldOptions(fieldDTO.getFieldOptions());
                        field.setRequired(fieldDTO.isRequired());
                        field.setDisplayOrder(fieldDTO.getDisplayOrder());
                        // Associer à la nouvelle catégorie
                        field.setCategory(savedCategory);
                        System.out.println("♻️ Réutilisation du champ existant: " + fieldDTO.getFieldName());
                    } else {
                        // ✅ CRÉER un nouveau champ
                        field = CategoryField.builder()
                                .fieldName(fieldDTO.getFieldName())
                                .fieldType(fieldDTO.getFieldType())
                                .fieldOptions(fieldDTO.getFieldOptions())
                                .required(fieldDTO.isRequired())
                                .displayOrder(fieldDTO.getDisplayOrder())
                                .category(savedCategory)
                                .build();
                        System.out.println("➕ Création nouveau champ: " + fieldDTO.getFieldName());
                    }

                    categoryFieldRepository.save(field);
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

            // ✅ 4. Gestion des champs - Permettre la réutilisation
            List<CategoryField> existingFields = categoryFieldRepository.findByCategoryId(id);
            Map<String, CategoryField> existingFieldsMap = existingFields.stream()
                    .collect(Collectors.toMap(CategoryField::getFieldName, field -> field));

            // Traiter les champs de la requête
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                for (CategoryFieldDTO fieldDTO : request.getFields()) {
                    CategoryField field;

                    // ✅ Vérifier si le champ existe déjà dans cette catégorie
                    if (existingFieldsMap.containsKey(fieldDTO.getFieldName())) {
                        // MISE À JOUR du champ existant dans cette catégorie
                        field = existingFieldsMap.get(fieldDTO.getFieldName());
                        field.setFieldType(fieldDTO.getFieldType());
                        field.setFieldOptions(fieldDTO.getFieldOptions());
                        field.setRequired(fieldDTO.isRequired());
                        field.setDisplayOrder(fieldDTO.getDisplayOrder());
                        System.out.println("🔄 Mise à jour champ: " + fieldDTO.getFieldName());
                    } else {
                        // ✅ Vérifier si le champ existe dans category_fields (autre catégorie)
                        Optional<CategoryField> globalField = categoryFieldRepository.findByFieldName(fieldDTO.getFieldName());

                        if (globalField.isPresent()) {
                            // ✅ RÉUTILISER le champ existant d'une autre catégorie
                            field = globalField.get();
                            field.setFieldType(fieldDTO.getFieldType());
                            field.setFieldOptions(fieldDTO.getFieldOptions());
                            field.setRequired(fieldDTO.isRequired());
                            field.setDisplayOrder(fieldDTO.getDisplayOrder());
                            field.setCategory(updatedCategory);
                            System.out.println("♻️ Réutilisation champ existant d'une autre catégorie: " + fieldDTO.getFieldName());
                        } else {
                            // ✅ CRÉER un nouveau champ
                            field = CategoryField.builder()
                                    .fieldName(fieldDTO.getFieldName())
                                    .fieldType(fieldDTO.getFieldType())
                                    .fieldOptions(fieldDTO.getFieldOptions())
                                    .required(fieldDTO.isRequired())
                                    .displayOrder(fieldDTO.getDisplayOrder())
                                    .category(updatedCategory)
                                    .build();
                            System.out.println("➕ Création nouveau champ: " + fieldDTO.getFieldName());
                        }
                    }

                    categoryFieldRepository.save(field);
                    existingFieldsMap.remove(fieldDTO.getFieldName());
                }
            }

            // Supprimer les champs orphelins (ceux qui ne sont plus dans la requête)
            if (!existingFieldsMap.isEmpty()) {
                List<Long> fieldIdsToDelete = existingFieldsMap.values().stream()
                        .map(CategoryField::getId)
                        .collect(Collectors.toList());
                categoryFieldRepository.deleteAllByIdInBatch(fieldIdsToDelete);
                System.out.println("🗑️ Suppression de " + fieldIdsToDelete.size() + " champs orphelins");
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

        // 2️⃣ Récupérer les champs existants de CETTE catégorie
        List<CategoryField> existingFieldsInCategory = categoryFieldRepository.findByCategoryId(categoryId);
        Set<String> existingFieldNamesInCategory = existingFieldsInCategory.stream()
                .map(CategoryField::getFieldName)
                .collect(Collectors.toSet());

        // 3️⃣ Vérifier UNIQUEMENT les NOUVEAUX champs (qui ne sont pas déjà dans cette catégorie)
        for (CategoryFieldDTO field : fields) {
            String fieldName = field.getFieldName();

            // Si le champ est déjà dans cette catégorie, c'est OK (c'est une mise à jour)
            if (existingFieldNamesInCategory.contains(fieldName)) {
                continue;
            }

            // ✅ Pour les NOUVEAUX champs, on permet la réutilisation des champs existants
            // Donc on ne vérifie PAS l'unicité globale
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

            List<CategoryField> fields = categoryFieldRepository.findByCategoryId(id);
            System.out.println("📊 " + fields.size() + " champs associés trouvés");

            if (!fields.isEmpty()) {
                List<Long> fieldIds = fields.stream()
                        .map(CategoryField::getId)
                        .collect(Collectors.toList());
                categoryFieldRepository.deleteAllByIdInBatch(fieldIds);
                categoryFieldRepository.flush();
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