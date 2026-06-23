package com.coralio.expense_management_microservice.entities;

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

    // ✅ Relation ManyToMany via la table de liaison
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CategoryFieldMapping> fieldMappings = new ArrayList<>();

    // ⚠️ On garde une méthode utilitaire pour ajouter un champ avec ses attributs
    public void addField(CategoryField field, boolean required, Integer displayOrder) {
        CategoryFieldMapping mapping = CategoryFieldMapping.builder()
                .category(this)
                .field(field)
                .required(required)
                .displayOrder(displayOrder != null ? displayOrder : fieldMappings.size() + 1)
                .build();
        fieldMappings.add(mapping);
    }

    // Méthode pour récupérer les champs (via les mappings)
    public List<CategoryField> getFields() {
        return fieldMappings.stream()
                .map(CategoryFieldMapping::getField)
                .collect(Collectors.toList());
    }
}