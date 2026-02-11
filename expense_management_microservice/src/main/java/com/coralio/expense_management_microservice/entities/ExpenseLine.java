package com.coralio.expense_management_microservice.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    // Transport
    private String depart;
    private String destination;
    private String transportType;

    // Hébergement
    private Integer nombreNuits;
    private String hotelName;

    // Restauration
    private Integer nombrePersonnes;
    private String repasType;

    // Carburant
    private Double kilometrage;
    private String vehicule;

    // Divers
    private String detail;

}