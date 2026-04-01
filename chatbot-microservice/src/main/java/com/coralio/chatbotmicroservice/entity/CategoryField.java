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
@Table(name = "category_fields")
public class CategoryField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fieldName;

    @Column(nullable = false)
    private String fieldType;

    @Column(length = 1000)
    private String fieldOptions;

    private boolean required;

    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

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