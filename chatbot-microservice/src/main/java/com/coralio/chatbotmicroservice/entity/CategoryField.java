package com.coralio.chatbotmicroservice.entity;

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
@Table(name = "category_fields",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fieldName"}))
public class CategoryField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fieldName;

    @Column(nullable = false)
    private String fieldType;      // TEXT, NUMBER, DATE, SELECT, TEXTAREA

    @Column(length = 1000)
    private String fieldOptions;   // Pour SELECT: "Train,Avion,Taxi,Voiture"

    // ❌ Supprimer required, displayOrder, et category_id
    // private boolean required;
    // private Integer displayOrder;
    // @ManyToOne private Category category;

    public String[] getOptionsArray() {
        if (fieldOptions == null || fieldOptions.isEmpty()) {
            return new String[0];
        }
        return fieldOptions.split(",");
    }

    public String getOptionsDisplay() {
        String[] options = getOptionsArray();
        if (options.length == 0) return "";
        return "Options: " + String.join(", ", options);
    }
}