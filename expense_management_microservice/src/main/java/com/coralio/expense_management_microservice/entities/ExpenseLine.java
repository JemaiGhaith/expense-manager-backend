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
    @Transient
    private String ocrText;

    @Transient
    private Map<String, Object> extractedJson;
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
    // Dans ExpenseLine.java, après les autres champs

        // ========== CHAMPS POUR LA VALIDATION IA ==========
        @Column(name = "validation_valid")
        private Boolean validationValid;          // true si validé, false si anomalie

        @Column(name = "validation_score")
        private Double validationScore;           // score de confiance (0-100)

        @Column(name = "validation_issues")
        private String validationIssues;          // problèmes détectés, séparés par "; "

        // ========== CHAMPS POUR LA DÉTECTION DE DOUBLON (optionnels, car déjà dans expense_duplicates) ==========
        @Column(name = "duplicate_detected")
        private Boolean duplicateDetected;

        @Column(name = "duplicate_score")
        private Double duplicateScore;

        @Column(name = "duplicate_matched_file")
        private String duplicateMatchedFile;

        // ========== CHAMPS POUR LA COMPARAISON FACTURE vs FORMULAIRE ==========
        @Column(columnDefinition = "TEXT")
        private String invoiceFormComparison;      // Résultat JSON complet de la comparaison

        @Column(name = "is_consistent_with_invoice")
        private Boolean isConsistentWithInvoice;    // true si facture cohérente avec formulaire

        @Column(name = "consistency_confidence")
        private Double consistencyConfidence;       // Score de confiance (0-1)

        @Column(name = "consistency_issues", columnDefinition = "TEXT")
        private String consistencyIssues;           // Problèmes détectés (séparés par "; ")
    // Suggestions de correction
    @Column(name = "suggested_amount")
    private Double suggestedAmount;

    @Column(name = "suggested_date")
    private LocalDate suggestedDate;

    @Column(name = "suggested_description")
    private String suggestedDescription;

    @Column(name = "suggested_category_id")
    private Long suggestedCategoryId;
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