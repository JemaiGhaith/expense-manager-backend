package com.coralio.chatbotmicroservice.model;


import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRule {
    private Long id;
    private String name;
    private BigDecimal plafond;
    private boolean active;
    private String icon;
    private List<CategoryField> fields;
    private List<String> specialRules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryField {
        private String fieldName;
        private String label;
        private String fieldType;
        private boolean required;
        private String description;
    }
}