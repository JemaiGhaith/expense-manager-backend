package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "category_field_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "field_id"}))
public class CategoryFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private CategoryField field;

    @Column(nullable = false)
    private boolean required;          // Obligatoire pour cette catégorie ?

    @Column(nullable = false)
    private Integer displayOrder;      // Ordre d'affichage pour cette catégorie
}