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
@Table(name = "category_fields")
public class CategoryField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ UN SEUL NOM - C'EST TOUT !
    // L'admin écrit exactement le nom de la colonne dans expense_lines
    // Exemples: "depart", "destination", "transportType", "nombreNuits",
    //           "hotelName", "nombrePersonnes", "repasType", "kilometrage",
    //           "vehicule", "detail"
    @Column(nullable = false)
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