package com.coralio.expense_management_microservice.dto;

import com.coralio.expense_management_microservice.entities.ExpenseLine;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class ExpenseLineDetailDTO {
    private Long id;
    private Long expenseNoteId;
    private Long categoryId;
    private Double amount;
    private LocalDate expenseDate;
    private String description;
    private String justificatifPath;

    // ✅ Nouveaux champs pour les anomalies
    private Boolean isAnomalyDepense;
    private String anomalyExpenseMessage;

    // ✅ Tous les champs dynamiques seront stockés ici
    private Map<String, Object> dynamicFields = new HashMap<>();

    public ExpenseLineDetailDTO(ExpenseLine line) {
        this.id = line.getId();
        this.expenseNoteId = line.getExpenseNoteId();
        this.categoryId = line.getCategoryId();
        this.amount = line.getAmount();
        this.expenseDate = line.getExpenseDate();
        this.description = line.getDescription();
        this.justificatifPath = line.getJustificatifPath();
        // ✅ Ajouter les champs d'anomalie
        this.isAnomalyDepense = line.getIsAnomalyDepense();
        this.anomalyExpenseMessage = line.getAnomalyExpenseMessage();
        // ✅ Copier tous les champs dynamiques
        if (line.getDynamicFields() != null) {
            this.dynamicFields = new HashMap<>(line.getDynamicFields());
        }
    }

    // ✅ Permet de sérialiser les champs dynamiques comme des propriétés directes
    @JsonAnyGetter
    public Map<String, Object> getDynamicFields() {
        return dynamicFields;
    }

    @JsonAnySetter
    public void setDynamicField(String key, Object value) {
        this.dynamicFields.put(key, value);
    }

    // ✅ Récupérer une valeur dynamique
    public Object get(String fieldName) {
        return dynamicFields.get(fieldName);
    }
}