package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "category_fields", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fieldName"}, name = "uk_category_fields_fieldname")
})
public class CategoryField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fieldName;

    @Column(nullable = false)
    private String fieldType;      // TEXT, NUMBER, DATE, SELECT, TEXTAREA

    @Column(length = 1000)
    private String fieldOptions;   // Pour SELECT: ["Train","Avion","Taxi","Voiture"]

    // ⚠️ On retire required et displayOrder, car ils sont maintenant dans le mapping
    // private boolean required;
    // private Integer displayOrder;

    // Relation inverse vers les mappings
    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CategoryFieldMapping> categoryMappings = new ArrayList<>();

    // ✅ On garde la contrainte d'unicité sur fieldName
}