package com.coralio.expense_management_microservice.entities;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "expense_lines")
public class ExpenseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_note_id")
    private Long expenseNoteId;

    @Column(name = "category_id")
    private Long categoryId;

    private Double amount;

    @Column(name = "expense_date")
    private LocalDate expenseDate;

    private String description;

    @Column(name = "justificatif_path")
    private String justificatifPath;

    // 🚗 TRANSPORT
    private String depart;
    private String destination;
    private String transportType;

    // 🏨 HÉBERGEMENT
    private Integer nombreNuits;
    private String hotelName;

    // 🍽 RESTAURATION
    private Integer nombrePersonnes;
    private String repasType;

    // ⛽ CARBURANT
    private Double kilometrage;
    private String vehicule;

    // 📦 DIVERS
    private String detail;

    @Column(name = "is_anomaly_depense")
    private Boolean isAnomalyDepense = false;

    @Column(name = "anomaly_expense_message", columnDefinition = "TEXT")
    private String anomalyExpenseMessage;


    // ✅ CHAMPS DYNAMIQUES - Pour les colonnes non déclarées
    @Transient
    @Builder.Default
    private Map<String, Object> dynamicFields = new HashMap<>();

    /**
     * ✅ Cette méthode capture TOUTES les propriétés JSON qui n'ont pas
     * de correspondance dans l'entité et les stocke dans dynamicFields
     */
    @JsonAnySetter
    public void handleUnknownProperties(String key, Object value) {
        dynamicFields.put(key, value);
    }

    public Object getDynamicField(String fieldName) {
        return dynamicFields.get(fieldName);
    }

    public void setDynamicField(String fieldName, Object value) {
        this.dynamicFields.put(fieldName, value);
    }

    public boolean hasDynamicField(String fieldName) {
        return dynamicFields.containsKey(fieldName);
    }


    public static String toColumnName(String fieldName) {
        if (fieldName == null) return null;
        return fieldName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * ✅ Récupère une valeur en essayant d'abord le nom exact,
     *    puis le nom converti en snake_case
     */
    public Object getFieldValue(String fieldName) {
        // 1️⃣ Essayer le champ standard avec le nom exact
        try {
            var field = ExpenseLine.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(this);
            if (value != null) return value;
        } catch (Exception e) {
            // Ignorer
        }

        // 2️⃣ Essayer les champs dynamiques avec le nom exact
        if (dynamicFields.containsKey(fieldName)) {
            return dynamicFields.get(fieldName);
        }

        // 3️⃣ Essayer les champs dynamiques avec le nom converti en snake_case
        String columnName = toColumnName(fieldName);
        if (dynamicFields.containsKey(columnName)) {
            return dynamicFields.get(columnName);
        }

        return null;
    }
    // Dans ExpenseLine.java - Ajouter cette méthode
    public Map<String, Object> getDynamicFields() {
        if (dynamicFields == null) {
            dynamicFields = new HashMap<>();
        }
        return dynamicFields;
    }
}