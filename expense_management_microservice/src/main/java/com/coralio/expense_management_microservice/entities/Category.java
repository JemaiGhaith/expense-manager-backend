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
@Table(name = "expense_categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;           // "Transport", "Hébergement", "Restauration", etc.

    private Double plafond;

    @Column(length = 500)
    private String description;

    private boolean active = true;

    // 🔥 Une catégorie a plusieurs champs
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default  // ← IMPORTANT pour que Lombok garde l'initialisation
    private List<CategoryField> fields = new ArrayList<>();

    public void addField(CategoryField field) {
        fields.add(field);
        field.setCategory(this);
    }
}