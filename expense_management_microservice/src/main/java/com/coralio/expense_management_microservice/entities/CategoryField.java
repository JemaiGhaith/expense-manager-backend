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
@Table(name = "category_fields", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fieldName"}, name = "uk_category_fields_fieldname")
})
public class CategoryField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ UN SEUL NOM - UNIQUE DANS TOUTE LA TABLE
    @Column(nullable = false, unique = true)
    private String fieldName;

    @Column(nullable = false)
    private String fieldType;      // TEXT, NUMBER, DATE, SELECT, TEXTAREA

    @Column(length = 1000)
    private String fieldOptions;   // Pour SELECT: ["Train","Avion","Taxi","Voiture"]

    private boolean required;      // Champ obligatoire ?

    private Integer displayOrder;  // Ordre d'affichage

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}