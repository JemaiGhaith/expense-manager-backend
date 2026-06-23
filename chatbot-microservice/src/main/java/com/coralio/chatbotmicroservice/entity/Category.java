package com.coralio.chatbotmicroservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "expense_categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private Double plafond;

    @Column(length = 500)
    private String description;

    private boolean active = true;

    // ✅ Relation via la table de liaison
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CategoryFieldMapping> fieldMappings = new ArrayList<>();

    // --- Méthodes utilitaires ---

    public List<CategoryField> getFields() {
        return fieldMappings.stream()
                .map(CategoryFieldMapping::getField)
                .collect(Collectors.toList());
    }

    public List<CategoryField> getRequiredFields() {
        return fieldMappings.stream()
                .filter(CategoryFieldMapping::isRequired)
                .map(CategoryFieldMapping::getField)
                .collect(Collectors.toList());
    }

    public List<String> getFieldNames() {
        return fieldMappings.stream()
                .map(m -> m.getField().getFieldName())
                .collect(Collectors.toList());
    }

    public String getFieldDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (CategoryFieldMapping mapping : fieldMappings) {
            CategoryField field = mapping.getField();
            sb.append(String.format("- %s (%s)%s\n",
                    field.getFieldName(),
                    field.getFieldType(),
                    mapping.isRequired() ? " [OBLIGATOIRE]" : " [optionnel]"
            ));
        }
        return sb.toString();
    }

    // Si vous avez besoin d'ajouter un champ depuis le code (par ex. pour tests)
    public void addField(CategoryField field, boolean required, Integer displayOrder) {
        CategoryFieldMapping mapping = CategoryFieldMapping.builder()
                .category(this)
                .field(field)
                .required(required)
                .displayOrder(displayOrder != null ? displayOrder : fieldMappings.size() + 1)
                .build();
        fieldMappings.add(mapping);
    }
}