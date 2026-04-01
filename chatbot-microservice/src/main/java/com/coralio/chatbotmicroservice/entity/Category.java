package com.coralio.chatbotmicroservice.entity;

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
    private String name;

    private Double plafond;

    @Column(length = 500)
    private String description;

    private boolean active = true;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CategoryField> fields = new ArrayList<>();

    public List<CategoryField> getRequiredFields() {
        return fields.stream()
                .filter(CategoryField::isRequired)
                .toList();
    }

    public List<String> getFieldNames() {
        return fields.stream()
                .map(CategoryField::getFieldName)
                .toList();
    }

    public String getFieldDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (CategoryField field : fields) {
            sb.append(String.format("- %s (%s)%s\n",
                    field.getFieldName(),
                    field.getFieldType(),
                    field.isRequired() ? " [OBLIGATOIRE]" : " [optionnel]"
            ));
        }
        return sb.toString();
    }
}