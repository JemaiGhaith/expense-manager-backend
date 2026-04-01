package com.coralio.chatbotmicroservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    private String depart;
    private String destination;
    private String transportType;
    private Integer nombreNuits;
    private String hotelName;
    private Integer nombrePersonnes;
    private String repasType;
    private Double kilometrage;
    private String vehicule;
    private String detail;

    public String getFormattedDate() {
        if (expenseDate == null) return "";
        return expenseDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public String getCategorySpecificInfo() {
        if (transportType != null) {
            return String.format("Transport: %s de %s à %s", transportType, depart, destination);
        }
        if (hotelName != null) {
            return String.format("Hôtel: %s (%d nuits)", hotelName, nombreNuits);
        }
        if (repasType != null) {
            return String.format("Repas: %s pour %d personnes", repasType, nombrePersonnes);
        }
        if (kilometrage != null) {
            return String.format("Trajet: %.0f km en %s", kilometrage, vehicule);
        }
        return description;
    }
}